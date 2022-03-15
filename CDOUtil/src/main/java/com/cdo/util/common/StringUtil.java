package com.cdo.util.common;

import com.cdoframework.cdolib.util.Function;

/**
 * 
 * @author KenelLiu
 *
 */
public class StringUtil{
  
  public static boolean isNotEmpty(CharSequence charSeq){
    return !isEmpty(charSeq);
  }
  
  public static boolean isEmpty(CharSequence charSeq){
	    return (charSeq == null) || (charSeq.length() == 0);
  }
  
  public static boolean isNotBlank(CharSequence cs){
    return !isBlank(cs);
  } 
  
  public static boolean isBlank(CharSequence cs)
  {
    int strLen;
    if ((cs == null) || ((strLen = cs.length()) == 0))
      return true;

    for (int i = 0; i < strLen; i++) {
      if (!Character.isWhitespace(cs.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static boolean isNumber(String str){
    return (str != null) && (str.trim().length() != 0) && (str.matches("\\d*"));
  }

  public static String join(Object... args) {
	StringBuilder builder = new StringBuilder();
	for(Object item : args) {
		builder.append(item);
	}
	return builder.toString();
} 


  
 public static boolean isMail(String strEmail){
//      String  check="^([a-z0-9A-Z]+[-|//._]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?//.)+[a-zA-Z]{2,}$"; 						
//      Pattern regex=Pattern.compile(check);   
//      Matcher matcher=regex.matcher(str);   
//      boolean isMatched=matcher.matches();   
//      if(isMatched){   
//          return true;  
//      }else{   
//          return false;
//      }   
		if(!strEmail.contains("@")){			
			return false;
		}else if(strEmail.length()<=3){
			return false;
		}else{
			String tmpStrEmail=strEmail.substring(1,strEmail.length()-1);
			if(tmpStrEmail.indexOf("@")==-1)
				return false;
		}
		return true;
	}

  public static String htmlEncode(String str){    
	  return Function.FormatTextForHTML(str);
  }    
}