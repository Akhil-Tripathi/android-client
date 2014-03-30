package com.bsb.hike.ui;

import java.io.File;
import java.net.URI;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.R;
import com.bsb.hike.db.HikeUserDatabase;
import com.bsb.hike.models.HikeFile.HikeFileType;
import com.bsb.hike.utils.ChangeProfileImageBaseActivity;
import com.bsb.hike.utils.Utils;

public class CreateNewGroupActivity extends ChangeProfileImageBaseActivity
{

	private SharedPreferences preferences;

	private String groupId;

	private ImageView groupImage;

	private EditText groupName;

	private Button doneBtn;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.create_new_group);

		setupActionBar();

		groupImage = (ImageView) findViewById(R.id.group_profile_image);
		groupName = (EditText) findViewById(R.id.group_name);
		groupName.addTextChangedListener(new TextWatcher()
		{

			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
			{

			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
			{

			}

			@Override
			public void afterTextChanged(Editable editable)
			{
				doneBtn.setEnabled(!TextUtils.isEmpty(editable));
			}
		});

		preferences = getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS, MODE_PRIVATE);

		String uid = preferences.getString(HikeMessengerApp.UID_SETTING, "");
		groupId = uid + ":" + System.currentTimeMillis();
	}

	private void setupActionBar()
	{
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);

		View actionBarView = LayoutInflater.from(this).inflate(R.layout.compose_action_bar, null);

		View backContainer = actionBarView.findViewById(R.id.back);

		TextView title = (TextView) actionBarView.findViewById(R.id.title);
		doneBtn = (Button) actionBarView.findViewById(R.id.post_btn);

		doneBtn.setVisibility(View.VISIBLE);
		doneBtn.setText(R.string.next_signup);
		doneBtn.setEnabled(false);

		title.setText(R.string.new_group);

		backContainer.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				onBackPressed();
			}
		});

		doneBtn.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				Intent intent = new Intent(CreateNewGroupActivity.this, ComposeChatActivity.class);
				intent.putExtra(HikeConstants.Extras.GROUP_NAME, groupName.getText().toString());
				intent.putExtra(HikeConstants.Extras.GROUP_ID, groupId);
				intent.putExtra(HikeConstants.Extras.CREATE_GROUP, true);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			}
		});

		actionBar.setCustomView(actionBarView);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		String path = null;
		if (resultCode != RESULT_OK)
		{
			return;
		}

		String directory = HikeConstants.HIKE_MEDIA_DIRECTORY_ROOT + HikeConstants.PROFILE_ROOT;
		/*
		 * Making sure the directory exists before setting a profile image
		 */
		File dir = new File(directory);
		if (!dir.exists())
		{
			dir.mkdirs();
		}

		String fileName = Utils.getTempProfileImageFileName(groupId);
		final String destFilePath = directory + "/" + fileName;

		File selectedFileIcon = null;

		switch (requestCode)
		{
		case HikeConstants.CAMERA_RESULT:
			/* fall-through on purpose */
		case HikeConstants.GALLERY_RESULT:
			Log.d("ProfileActivity", "The activity is " + this);
			if (requestCode == HikeConstants.CAMERA_RESULT)
			{
				String filePath = preferences.getString(HikeMessengerApp.FILE_PATH, "");
				selectedFileIcon = new File(filePath);

				/*
				 * Removing this key. We no longer need this.
				 */
				Editor editor = preferences.edit();
				editor.remove(HikeMessengerApp.FILE_PATH);
				editor.commit();
			}
			if (requestCode == HikeConstants.CAMERA_RESULT && !selectedFileIcon.exists())
			{
				Toast.makeText(getApplicationContext(), R.string.error_capture, Toast.LENGTH_SHORT).show();
				return;
			}
			boolean isPicasaImage = false;
			Uri selectedFileUri = null;
			if (requestCode == HikeConstants.CAMERA_RESULT)
			{
				path = selectedFileIcon.getAbsolutePath();
			}
			else
			{
				selectedFileUri = data.getData();
				if (Utils.isPicasaUri(selectedFileUri.toString()))
				{
					isPicasaImage = true;
					path = Utils.getOutputMediaFile(HikeFileType.PROFILE, null, false).getAbsolutePath();
				}
				else
				{
					String fileUriStart = "file://";
					String fileUriString = selectedFileUri.toString();
					if (fileUriString.startsWith(fileUriStart))
					{
						selectedFileIcon = new File(URI.create(fileUriString));
						/*
						 * Done to fix the issue in a few Sony devices.
						 */
						path = selectedFileIcon.getAbsolutePath();
					}
					else
					{
						path = Utils.getRealPathFromUri(selectedFileUri, this);
					}
				}
			}
			if (TextUtils.isEmpty(path))
			{
				Toast.makeText(getApplicationContext(), R.string.error_capture, Toast.LENGTH_SHORT).show();
				return;
			}
			if (!isPicasaImage)
			{
				Utils.startCropActivity(this, path, destFilePath);
			}
			else
			{
				/*
				 * TODO handle picasa case.
				 */
				Toast.makeText(getApplicationContext(), R.string.error_capture, Toast.LENGTH_SHORT).show();
				return;
			}
			break;
		case HikeConstants.CROP_RESULT:
			String finalDestFilePath = data.getStringExtra(MediaStore.EXTRA_OUTPUT);
			if (finalDestFilePath == null)
			{
				Toast.makeText(getApplicationContext(), R.string.error_setting_profile, Toast.LENGTH_SHORT).show();
				return;
			}

			Bitmap tempBitmap = Utils.scaleDownImage(finalDestFilePath, HikeConstants.SIGNUP_PROFILE_IMAGE_DIMENSIONS, true);

			groupImage.setImageBitmap(Utils.getCircularBitmap(tempBitmap));

			/*
			 * Saving the icon in the DB.
			 */
			byte[] bytes = Utils.bitmapToBytes(tempBitmap, CompressFormat.JPEG, 100);
			HikeUserDatabase.getInstance().setIcon(groupId, bytes, false);

			break;
		}
	}
}
