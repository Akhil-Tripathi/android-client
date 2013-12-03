package com.bsb.hike.filetransfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.R;
import com.bsb.hike.HikeConstants.FTResult;
import com.bsb.hike.models.HikeFile.HikeFileType;

public abstract class FileTransferBase implements Callable<FTResult>
{
	public enum FTState
	{
		NOT_STARTED, IN_PROGRESS, // DOWNLOADING OR UPLOADING
		PAUSED, CANCELLED, COMPLETED, ERROR
	}

	protected static String NETWORK_ERROR_1 = "Connection timed out";
	
	protected static String NETWORK_ERROR_2 = "Unable to resolve host";
	
	protected boolean retry = true; // this will be used when network fails and you have to retry
	
	protected short retryAttempts = 0;
	
	protected short MAX_RETRY_ATTEMPTS = 5;
	
	protected int reconnectTime = 0;
	
	protected int MAX_RECONNECT_TIME = 30; // in seconds
	
	protected Handler handler;
	
	protected int progressPercentage;
	
	protected Object userContext = null;
	
	protected Context context;

	// this will be used for filename in download and upload both
	protected File mFile;
	
	protected String fileKey; // this is used for download from server , and in upload too
	
	protected File stateFile; // this represents state file in which file state will be saved

	protected volatile FTState _state;

	protected long msgId;

	protected HikeFileType hikeFileType;

	protected int _totalSize = 0;

	protected int _bytesTransferred = 0;
	
	protected ConcurrentHashMap<Long, FutureTask<FTResult>> fileTaskMap;
	
	protected FileTransferBase(Handler handler,ConcurrentHashMap<Long, FutureTask<FTResult>> fileTaskMap,Context ctx, File destinationFile, long msgId, HikeFileType hikeFileType)
	{
		this.handler = handler;
		this.mFile = destinationFile;
		this.msgId = msgId;
		this.hikeFileType = hikeFileType;
		context = ctx;
		this.fileTaskMap = fileTaskMap;
	}

	protected void setFileTotalSize(int ts)
	{
		_totalSize = ts;
	}

	// this will be used for both upload and download
	protected void incrementBytesTransferred(int value)
	{
		_bytesTransferred += value;
	}
	protected void setBytesTransferred(int value)
	{
		_bytesTransferred = value;
	}

	protected void saveFileState()
	{
		FileSavedState fss = new FileSavedState(_state, _totalSize, _bytesTransferred);
		try
		{
			FileOutputStream fileOut = new FileOutputStream(stateFile);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(fss);
			out.close();
			fileOut.close();
		}
		catch (IOException i)
		{
			i.printStackTrace();
		}
	}

	protected void saveFileState(String uuid)
	{
		FileSavedState fss = new FileSavedState(_state, _totalSize, _bytesTransferred,uuid);
		try
		{
			FileOutputStream fileOut = new FileOutputStream(stateFile);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(fss);
			out.close();
			fileOut.close();
		}
		catch (IOException i)
		{
			i.printStackTrace();
		}
	}
	
	protected void deleteStateFile()
	{
		if(stateFile!= null && stateFile.exists())
			stateFile.delete();
	}
	
	protected void setState(FTState mState)
	{
		// if state is completed we will not change it '
		if (!mState.equals(FTState.COMPLETED))
			_state = mState;
	}
	
	protected boolean shouldRetry()
	{
		if (retry && retryAttempts < MAX_RETRY_ATTEMPTS)
		{
			// make first attempt within first 5 seconds
			if (reconnectTime == 0)
			{
				Random random = new Random();
				reconnectTime = random.nextInt(5) + 1;
			}
			else
			{
				reconnectTime *= 2;
			}
			reconnectTime = reconnectTime > MAX_RECONNECT_TIME ? MAX_RECONNECT_TIME : reconnectTime;
			try
			{
				Thread.sleep(reconnectTime);
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			retryAttempts++;
			Log.d(getClass().getSimpleName(),"Returning true on retry attempt No. " + retryAttempts);
			return true;
		}
		else
		{
			Log.d(getClass().getSimpleName(), "Returning false on retry attempt No. " + retryAttempts);
			return false;
		}
	}
}
