package com.lede.tech.rocketmq.easyclient.common.exception;

/**
 * 
 * @Desc 
 * @Author ykhu
 */
@SuppressWarnings("serial")
public class MqBussinessException extends Exception
{
	public MqBussinessException()
	{
		super();
	}

	public MqBussinessException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public MqBussinessException(String message)
	{
		super(message);
	}

	public MqBussinessException(Throwable cause)
	{
		super(cause);
	}

	public MqBussinessException(Object message)
	{
		super(message.toString());
	}
}
