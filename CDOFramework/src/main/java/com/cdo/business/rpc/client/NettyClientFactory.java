package com.cdo.business.rpc.client;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.bag.SynchronizedBag;
import org.apache.log4j.Logger;

import com.cdo.business.rpc.client.proto.RPCClientHandler;
import com.cdo.business.rpc.client.xml.XMLRPCClientHandler;
import com.cdo.business.rpc.route.CircleRPCQueue;
import com.cdo.google.handle.CDOProtobufDecoder;
import com.cdo.google.handle.CDOProtobufEncoder;
import com.cdo.util.constants.Constants;
import com.cdo.util.system.SystemUtil;
import com.cdo.xml.handle.CDOXmlDecoder;
import com.cdo.xml.handle.CDOXmlEncoder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
//import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
//import io.netty.handler.ssl.SslContext;
//import io.netty.handler.ssl.SslContextBuilder;
//import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.internal.SystemPropertyUtil;

public class NettyClientFactory {
	private final static Logger logger=Logger.getLogger(NettyClientFactory.class);	
	private ExecutorService executor=Executors.newScheduledThreadPool(1);
	private RouteManager routeManager=RouteManager.getInstance();
	
	private static final NettyClientFactory clientFactory=new NettyClientFactory();
	//????????????????????????????????????????????????????????????.
	private  Bootstrap bootstrap;
	
	private  int channelThread;	
	private  int retryInterval;//?????????????????????????????????????????? ????????????  ?????? 5
	private  int retryCount;//?????? ????????????.?????????????????????3
	public int maxClientCount;//???jvm???   client ??????????????????????????????  ???????????????remoteAddress,??????1
	
	//retryMap???????????? remoteAddress-localAddress ???????????????
	private Map<String,Integer>  retryMap=new HashMap<String,Integer>();
	//
	private Map<String,byte[]> loclMap=new HashMap<String,byte[]>();
	
	private   static Map<String, CircleRPCQueue<ClientHandler>> routeMap=new HashMap<String,CircleRPCQueue<ClientHandler>>();
	
	private  Class<? extends  io.netty.channel.socket.SocketChannel> channelClass; 
	 
	private NettyClientFactory(){
		channelThread=Math.max(2,SystemPropertyUtil.getInt(Constants.Netty.THREAD_CLIENT_WORK,Runtime.getRuntime().availableProcessors()));
		retryInterval=Math.max(5, SystemPropertyUtil.getInt(Constants.Netty.THREAD_CLIENT_RETRY_INTERVAL,5));
		retryCount=Math.max(3, SystemPropertyUtil.getInt(Constants.Netty.THREAD_CLIENT_RETRY_COUNT,3));
		maxClientCount=Math.max(1, SystemPropertyUtil.getInt(Constants.Netty.THREAD_CLIENT_CONNECT_ConnCount,1));
		init(channelThread);
		if(logger.isInfoEnabled())
			logger.info("NettyClientFactory init channelThread="+channelThread+",retryInterval="+retryInterval+",retryCount="+retryCount+",maxClientCount="+maxClientCount);
	}
	
	public static final NettyClientFactory getDefaultInstance(){
		return clientFactory;
	}
	
	
	/**
	 * ??????remoteAddress ?????????????????????
	 * @param remoteAddress
	 */
	public void connect(String remoteAddress){
		String[] hostPort=remoteAddress.split(":");
		String host=hostPort[0].trim();
		int port=Integer.parseInt(hostPort[1].trim());		
		ChannelFuture future = bootstrap.connect(host, port);	    
	    channelFutureListener(future,remoteAddress);
	}
	/**
	 * ??????remoteAddress ????????????????????????????????????????????????
	 * @param remoteAddress
	 */
    public    void  createMutiClient(final String remoteAddress){
    	 byte[] lock=loclMap.get(remoteAddress)==null?new byte[0]:loclMap.get(remoteAddress);
    	 loclMap.put(remoteAddress, lock);
    	 synchronized(lock){
		   	 ExecutorService executor=Executors.newScheduledThreadPool(1);
		   	 executor.submit(new Runnable() {			
					@Override
					public void run() {
		   			for(int k=0;k<maxClientCount;k++){    				
		   				connect(remoteAddress);	   				
		   				try{Thread.sleep(1000);}catch(Exception ex){}
		   			}
					}
				});
		   	executor.shutdown(); 
		   	loclMap.remove(remoteAddress);
    	 }
   }

   public Map<String, CircleRPCQueue<ClientHandler>> getRouteMap(){
    	return routeMap;
    }
    
	/**
	 * ??????jvm  ???????????? NettyClientFactory??????,???????????????Bootstrap
	 * @param channelThread  ?????????????????????	 
	 * *  	
	 */
	void init(int channelThread){		
		try {
				EventLoopGroup workerGroup =null;	
			
		        if(SystemUtil.isLinux()){
		            workerGroup = new EpollEventLoopGroup(channelThread);    
		            channelClass=EpollSocketChannel.class;
		        }else{		           
		            workerGroup = new NioEventLoopGroup(channelThread);  
		            channelClass=NioSocketChannel.class;
		        }	

			    bootstrap = new Bootstrap();			    
			    bootstrap.group(workerGroup)
			    		.channel(channelClass)
			    		.option(ChannelOption.TCP_NODELAY, true)  
			    		.option(ChannelOption.SO_KEEPALIVE, true)
			    		.handler(new ChannelInitializer<SocketChannel>(){
							@Override
							protected void initChannel(SocketChannel ch) throws Exception {
						        ChannelPipeline p = ch.pipeline();			       
						        p.addLast(new ChannelInboundHandlerAdapter() {
							          @Override
							          public void channelInactive(ChannelHandlerContext ctx) throws Exception {
							            super.channelInactive(ctx);	
							            SocketChannel socketChannel=(SocketChannel)ctx.channel();		
							            InetSocketAddress remoteAddress=socketChannel.remoteAddress();	
							            InetSocketAddress localAddress=socketChannel.localAddress();	
							            String serverAddress=remoteAddress.getHostString()+":"+remoteAddress.getPort();
							            String clientAddress=localAddress.getHostString()+":"+localAddress.getPort();
							            //?????????????????????????????????
							            boolean flag=routeManager.removeRPCClient(serverAddress, ctx.channel().pipeline().get(RPCClientHandler.class));
							            if(!flag){
								        	  logger.warn("delete handle to CircleRPCQueue fail,maybe not found in queue");					        	  
								          }else{
								        	  logger.info("delete handle from CircleRPCQueue sucess ");
								          }
							            logger.warn( "ctx is channelInactive,after 5 secondes retry connection remote=["+serverAddress+"],local="+clientAddress);
							            executor.execute(new Runnable() {								
												@Override
												public void run() {
													try{
														TimeUnit.SECONDS.sleep(retryInterval);
													}catch(Exception ex){}										
													doConnect(serverAddress,clientAddress);								
												}
											});	  
							          }
							        });		
						        
						p.addLast("encoder",new CDOProtobufEncoder());
						p.addLast("decoder",new CDOProtobufDecoder());  
				        p.addLast("ideaHandler",new IdleStateHandler(90,15,0));
				        p.addLast("heartbeat",new HeartbeatClientHandler());
				        p.addLast("handle",new RPCClientHandler());				
					}            	 
			      });
		    }catch(Exception ex){
		      logger.error(ex.getMessage(), ex);
		    }	
		logger.info("client work total channel="+channelThread+",bootstrap="+bootstrap);
	}
	
	
	
	private void doConnect(String serverAddress,String clientAddress) {
        String key=serverAddress+"-"+clientAddress;
        int retry=retryMap.get(key)==null?0:retryMap.get(key).intValue();
        //?????????jvm ?????????????????? ?????? ?????????????????????????????????????????????,?????????????????????????????????.
        if(routeManager.isFullCircleQueue(serverAddress)){
        	if(logger.isDebugEnabled())
        		logger.debug(" remote=["+serverAddress+"],queue conentions is full. ");
        	return;
        }
        retry++;//??????????????????
        retryMap.put(key, retry);
        if(clientAddress!=null){
	        InetSocketAddress remote=new InetSocketAddress(serverAddress.split(":")[0], Integer.valueOf(serverAddress.split(":")[1]));
	        InetSocketAddress local=new InetSocketAddress(clientAddress.split(":")[0], Integer.valueOf(clientAddress.split(":")[1]));
		    ChannelFuture future = bootstrap.connect(remote,local);
		    channelFutureListener(future,serverAddress,clientAddress,retry);
        }else{	        
		    ChannelFuture future = bootstrap.connect(serverAddress.split(":")[0],Integer.valueOf(serverAddress.split(":")[1]));
		    channelFutureListener(future,serverAddress);
        }
	
	  }
	
	
	private void channelFutureListener(ChannelFuture future,String remoteAddress){		
		 channelFutureListener(future,remoteAddress,null,0);
	}
	
	private void channelFutureListener(ChannelFuture future,String serverAddress,String clientAddress,int retry ){
		
	    future.addListener(new ChannelFutureListener() {
		      public void operationComplete(ChannelFuture f) throws Exception {	
		    	 String key=serverAddress+"-"+clientAddress;
		         if (f.isSuccess()) {
			    	  Channel channel=f.channel();			         			         			        
			    	  RPCClientHandler handle=f.channel().pipeline().get(RPCClientHandler.class);
			          //???????????????
			          boolean flag=routeManager.addRPCClientHandler(serverAddress, handle);	   
			          if(!flag){
			        	  logger.warn("add handle to CircleRPCQueue fail,maybe queue is full,channel will be closed ");
			        	  channel.close();
			          }else{
			        	  logger.info("Started  Client Success  connection,channel="+channel);
			          }
			          //??????????????????,??????????????????,???????????????????????????
			     	  retryMap.remove(key);
		         } else {	          
		        	 //??????????????????...??????????????????.....	
			          f.channel().close();			        
			          if(retry==retryCount){
			          	 retryMap.remove(key);
			          	//??????????????????  ????????????...
			          	logger.warn(" already connection  "+retryCount+" times   failed, remote=["+serverAddress+"],local="+clientAddress+",noretry conention ");
			          	return;
			          }else{
			        	  if(retry>0)
				             logger.warn("retry "+retry+" times  connection fail, remote=["+serverAddress+"],local="+clientAddress);
			          }
			          executor.execute(new Runnable() {								
							@Override
							public void run() {
								try{
									TimeUnit.SECONDS.sleep(retryInterval);
								}catch(Exception ex){}										
								doConnect(serverAddress,clientAddress);								
							}
						});          
		        }
		      }
		    });
		
	}
}
