package com.bsb.hike.utils;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.maps.MapActivity;

public abstract class HikeAppStateBaseMapActivity extends MapActivity
{

	private static final String TAG = "HikeAppState";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		HikeAppStateUtils.onCreate(this);
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onResume()
	{
		HikeAppStateUtils.onResume(this);
		super.onResume();
	}

	@Override
	protected void onStart()
	{
		HikeAppStateUtils.onStart(this);
		super.onStart();
	}

	@Override
	public void onBackPressed()
	{
		HikeAppStateUtils.onBackPressed();
		super.onBackPressed();
	}

	@Override
	protected void onStop()
	{
		HikeAppStateUtils.onStop(this);
		super.onStop();
	}

	@Override
	public void finish()
	{
		HikeAppStateUtils.finish();
		super.finish();
	}

	@Override
	public void startActivityForResult(Intent intent, int requestCode)
	{
		HikeAppStateUtils.startActivityForResult();
		super.startActivityForResult(intent, requestCode);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		HikeAppStateUtils.onActivityResult(this);
		super.onActivityResult(requestCode, resultCode, data);
	}
}
