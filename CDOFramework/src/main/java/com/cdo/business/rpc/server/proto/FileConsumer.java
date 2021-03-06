package com.cdo.business.rpc.server.proto;

import java.io.File;
import java.util.List;
import java.util.concurrent.TransferQueue;

import org.apache.log4j.Logger;

import com.cdo.business.BusinessService;
import com.cdo.business.rpc.RPCFile;
import com.cdo.google.Header;
import com.cdo.google.ParseProtoCDO;
import com.cdo.google.fileHandle.CDOFileMessage;
import com.cdo.google.handle.ProtoProtocol;
import com.cdo.google.protocol.GoogleCDO;
import com.cdoframework.cdolib.base.Return;
import com.cdoframework.cdolib.data.cdo.CDO;
import com.cdoframework.cdolib.servicebus.IServiceBus;
import com.cdoframework.cdolib.servicebus.ITransService;

public class FileConsumer implements Runnable {
	private static Logger logger=Logger.getLogger(FileConsumer.class);
	private boolean stop=false;
	private String name;
	private TransferQueue<HandleFileData> lnkTransQueue;
	private RPCFileServerHandler handle;
	private IServiceBus serviceBus;
	public boolean isStop() {
		return stop;
	}

	public void setStop(boolean stop) {
		this.stop = stop;
	}

	public FileConsumer(String name,TransferQueue<HandleFileData> lnkTransQueue,RPCFileServerHandler handle){
		this.name=name;
		this.lnkTransQueue=lnkTransQueue;
		this.handle=handle;
		this.serviceBus=BusinessService.getInstance().getServiceBus();
	}
	@Override
	public void run() {	
		while(true){
			HandleFileData handleFileData=null;
			try {
				if(logger.isDebugEnabled())
					logger.debug("name="+name+" is waiting to take element....,Thread="+Thread.currentThread().getId());
				handleFileData=lnkTransQueue.take();
				if(logger.isDebugEnabled())							
					logger.debug("name="+name+" received Element....,Thread="+Thread.currentThread().getId());
			} catch (InterruptedException  ex) {
				if(logger.isDebugEnabled())
					logger.debug("name="+name+"  Thread break,sleep 0.5 seconds,continue run()");
				try{Thread.sleep(500);}catch(Exception e){}									
			}catch(Exception ex){
				if(lnkTransQueue==null || stop){
					logger.warn("name="+name+",stop="+stop+",lnkTransQueue="+lnkTransQueue+",break Consumer queue");					
					break;
				}
			}
			if(handleFileData!=null){
				process(handleFileData.getProto(), handleFileData.getListFile());				
			}
		}
	}
	

	 void process(GoogleCDO.CDOProto proto,List<File> listFile){
		CDO cdoRequest=null;				
		CDO cdoOutput=null;
		GoogleCDO.CDOProto.Builder resProtoBuiler=null;
		try{		
	    	//?????????????????????,?????????????????????.?????????????????????????????????
			List<File> files=null;			
			String strServiceName=null;
			String strTransName=null;			
			//??????cdo
	    	try{
				cdoRequest=ParseProtoCDO.ProtoParse.parse(proto);
				strServiceName=cdoRequest.exists(ITransService.SERVICENAME_KEY)?cdoRequest.getStringValue(ITransService.SERVICENAME_KEY):"null";
				strTransName=cdoRequest.exists(ITransService.TRANSNAME_KEY)?cdoRequest.getStringValue(ITransService.TRANSNAME_KEY):"null";				
				//?????????????????????  ??????......
				cdoOutput=handleTrans(cdoRequest,listFile,strServiceName,strTransName);	   
	    		files=RPCFile.readFileFromCDO(cdoOutput.getCDOValue("cdoResponse"));		    		
	    		resProtoBuiler=cdoOutput.toProtoBuilder();		    		
	    	}catch(Throwable ex){
			    //???????????? ,..???????????????,???????????????????????????
	    		logger.error(ex.getMessage(), ex);
	    		if(cdoOutput==null)
	    			cdoOutput=new CDO();
	    		setFailOutCDO(cdoOutput, "strServiceName="+strServiceName+",strTransName="+strTransName+" RPCServer ????????????:"+ex.getMessage());
	    		resProtoBuiler=cdoOutput.toProtoBuilder();
	    	}   	
	    	//????????????,????????????
			resProtoBuiler.setCallId(proto.getCallId());				
			Header resHeader=new Header();
			resHeader.setType(ProtoProtocol.TYPE_CDO);
			CDOFileMessage resMessage=new CDOFileMessage();
			resMessage.setHeader(resHeader);
			resMessage.setBody(resProtoBuiler.build());
			resMessage.setFiles(files);
			//????????????
			handle.writeAndFlush(resMessage);
		}finally{
			cdoRequest.deepRelease();
			cdoOutput.deepRelease();
			resProtoBuiler=null;
			proto=null;
		}		
	}
		/**
		 * ?????????????????????????????????????????????????????????????????? cdoReturn???????????????,cdoResponse???????????????
		 * ????????? cdoOutput ??????2???cdo??????,cdoReturn,cdoResponse
		 * cdoReturn ?????? ???????????? 
		 * ?????? cdoResponse????????????
		 * ????????????  ?????? ?????? ????????? ???????????????
		 * @param cdoRequest
		 * @param listFile
		 * @param strServiceName
		 * @param strTransName
		 * @return
		 */
		private CDO handleTrans(CDO cdoRequest,List<File> listFile,String strServiceName,String strTransName){		
			CDO cdoOutput=new CDO();
			CDO cdoResponse=new CDO();;		
			try{							
				//???client?????????????????? ?????????cdoRequest???
				RPCFile.setFile2CDO(cdoRequest,listFile);
				//????????????
				Return ret=serviceBus.handleTrans(cdoRequest, cdoResponse);
				if(ret==null){			
					setFailOutCDO(cdoOutput," ret is null,Request method :strServiceName="+strServiceName+",strTransName="+strTransName);	
					logger.error("ret is null,Request method:strServiceName="+strServiceName+",strTransName="+strTransName);
				}else{
					CDO cdoReturn=new CDO();
					cdoReturn.setIntegerValue("nCode",ret.getCode());
					cdoReturn.setStringValue("strText",ret.getText());
					cdoReturn.setStringValue("strInfo",ret.getInfo());

					cdoOutput.setCDOValue("cdoReturn",cdoReturn);
					cdoOutput.setCDOValue("cdoResponse", cdoResponse);					
				}
			}catch(Throwable ex){
				logger.error(ex.getMessage(), ex);	
				setFailOutCDO(cdoOutput,"strServiceName="+strServiceName+",strTransName="+strTransName+"???????????????????????????:"+ex.getMessage());
			}	
			return cdoOutput;
		}

		private void setFailOutCDO(CDO cdoOutput,String message){		
			CDO cdoReturn=new CDO();
			cdoReturn.setIntegerValue("nCode",-1);
			cdoReturn.setStringValue("strText",message);
			cdoReturn.setStringValue("strInfo",message);
			
			cdoOutput.setCDOValue("cdoReturn",cdoReturn);
			cdoOutput.setCDOValue("cdoResponse",new CDO());		
		}
}
