package com.bsb.hike.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.R;
import com.bsb.hike.utils.AccountUtils;

public class WelcomeActivity extends Activity {
	public class AccountCreateActivity extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Void... arg0) {
			AccountUtils.AccountInfo accountInfo = AccountUtils.registerAccount();

			if (accountInfo != null) {
				String token = accountInfo.token;
				String msisdn = accountInfo.msisdn;
				AccountUtils.setToken(token);
				SharedPreferences settings = getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS, 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putString(HikeMessengerApp.TOKEN_SETTING, token);
				editor.putString(HikeMessengerApp.MSISDN_SETTING, msisdn);
				editor.commit();
			}

			return Boolean.TRUE;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			mDialog.dismiss();
			startActivity(new Intent(WelcomeActivity.this, AccountCreateSuccess.class));
			finish();
		}
	}

	private ProgressDialog mDialog;

	@Override
	public void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		setContentView(R.layout.welcomescreen);
	}

	public void onClick(View v) {
		mDialog = ProgressDialog.show(this, null, getText(R.string.determining_phone_number));
		AccountCreateActivity aca = new AccountCreateActivity();
		aca.execute();
	}
}
