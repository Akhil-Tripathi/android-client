package com.bsb.hike.ui;

import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.HikePubSub.Listener;
import com.bsb.hike.R;
import com.bsb.hike.adapters.EmoticonAdapter.EmoticonType;
import com.bsb.hike.adapters.MoodAdapter;
import com.bsb.hike.adapters.StatusEmojiAdapter;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.http.HikeHttpRequest;
import com.bsb.hike.http.HikeHttpRequest.HikeHttpCallback;
import com.bsb.hike.http.HikeHttpRequest.RequestType;
import com.bsb.hike.models.StatusMessage;
import com.bsb.hike.models.StatusMessage.StatusMessageType;
import com.bsb.hike.tasks.HikeHTTPTask;
import com.bsb.hike.utils.AuthSocialAccountBaseActivity;
import com.bsb.hike.utils.EmoticonConstants;
import com.bsb.hike.utils.EmoticonTextWatcher;
import com.bsb.hike.utils.Utils;

public class StatusUpdate extends AuthSocialAccountBaseActivity implements
		Listener {

	private class ActivityTask {
		int moodId = -1;
		HikeHTTPTask hikeHTTPTask = null;
		boolean fbSelected = false;
		boolean twitterSelected = false;
		boolean emojiShowing = false;
		boolean moodShowing = false;
	}

	private ActivityTask mActivityTask;
	private SharedPreferences preferences;
	private ProgressDialog progressDialog;

	private String[] pubsubListeners = { HikePubSub.SOCIAL_AUTH_COMPLETED,
			HikePubSub.SOCIAL_AUTH_FAILED, HikePubSub.STATUS_POST_REQUEST_DONE };
	private ViewGroup moodParent;
	private Button titleBtn;
	private TextView mTitleView;
	private ImageView avatar;
	private ViewGroup emojiParent;
	private EditText statusTxt;

	@Override
	public Object onRetainNonConfigurationInstance() {
		return mActivityTask;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.status_dialog);

		Object o = getLastNonConfigurationInstance();

		if (o instanceof ActivityTask) {
			mActivityTask = (ActivityTask) o;
			if (mActivityTask.hikeHTTPTask != null) {
				progressDialog = ProgressDialog.show(this, null, getResources()
						.getString(R.string.updating_status));
			}
		} else {
			mActivityTask = new ActivityTask();
		}

		preferences = getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS,
				MODE_PRIVATE);

		emojiParent = (ViewGroup) findViewById(R.id.emoji_container);
		moodParent = (ViewGroup) findViewById(R.id.mood_parent);
		titleBtn = (Button) findViewById(R.id.title_icon);
		mTitleView = (TextView) findViewById(R.id.title);

		titleBtn.setText(R.string.post);
		titleBtn.setEnabled(false);
		titleBtn.setVisibility(View.VISIBLE);

		findViewById(R.id.button_bar_2).setVisibility(View.VISIBLE);

		mTitleView.setText(R.string.status);

		avatar = (ImageView) findViewById(R.id.avatar);
		avatar.setImageResource(R.drawable.ic_text_status);
		avatar.setBackgroundResource(R.drawable.bg_status_type);

		final TextView charCounter = (TextView) findViewById(R.id.char_counter);

		statusTxt = (EditText) findViewById(R.id.status_txt);

		String statusHint = getStatusDefaultHint();

		statusTxt.setHint(statusHint);
		statusTxt.setText("");

		charCounter.setText(Integer
				.toString(HikeConstants.MAX_TWITTER_POST_LENGTH
						- statusTxt.length()));

		setMood(mActivityTask.moodId);

		statusTxt.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				toggleEnablePostButton();
				charCounter.setText(Integer
						.toString(HikeConstants.MAX_TWITTER_POST_LENGTH
								- s.length()));
			}
		});
		statusTxt.addTextChangedListener(new EmoticonTextWatcher());

		toggleEnablePostButton();

		View fb = findViewById(R.id.post_fb_btn);
		View twitter = findViewById(R.id.post_twitter_btn);

		fb.setSelected(mActivityTask.fbSelected);
		twitter.setSelected(mActivityTask.twitterSelected);

		if (mActivityTask.emojiShowing) {
			showEmojiSelector();
		} else if (mActivityTask.moodShowing) {
			showMoodSelector();
		}

		HikeMessengerApp.getPubSub().addListeners(this, pubsubListeners);
	}

	public void onTitleIconClick(View v) {
		if (isEmojiOrMoodLayoutVisible()) {
			hideEmojiOrMoodLayout();
		} else {
			postStatus();
		}
	}

	public void onTwitterClick(View v) {
		setSelectionSocialButton(false, !v.isSelected());

		if (!v.isSelected()
				|| preferences.getBoolean(
						HikeMessengerApp.TWITTER_AUTH_COMPLETE, false)) {
			return;
		}
		startActivity(new Intent(StatusUpdate.this, TwitterAuthActivity.class));
	}

	public void onFacebookClick(View v) {
		setSelectionSocialButton(true, !v.isSelected());
		if (!v.isSelected()
				|| preferences.getBoolean(
						HikeMessengerApp.FACEBOOK_AUTH_COMPLETE, false)) {
			return;
		}
		startFBAuth(false);
	}

	public void onEmojiClick(View v) {
		showEmojiSelector();
	}

	public void onMoodClick(View v) {
		showMoodSelector();
	}

	@Override
	public void onBackPressed() {
		if (isEmojiOrMoodLayoutVisible()) {
			hideEmojiOrMoodLayout();
		} else {
			super.onBackPressed();
			if (getIntent().getBooleanExtra(
					HikeConstants.Extras.FROM_CONVERSATIONS_SCREEN, false)) {
				overridePendingTransition(R.anim.no_animation,
						R.anim.slide_down_noalpha);
			}
		}
	}

	private boolean isEmojiOrMoodLayoutVisible() {
		return ((moodParent.getVisibility() == View.VISIBLE) || (findViewById(
				R.id.emoji_container).getVisibility() == View.VISIBLE));
	}

	private void hideEmojiOrMoodLayout() {
		if (moodParent.getVisibility() == View.VISIBLE) {
			mActivityTask.moodShowing = false;
			moodParent.setVisibility(View.GONE);
		} else if (emojiParent.getVisibility() == View.VISIBLE) {
			mActivityTask.emojiShowing = false;
			emojiParent.setVisibility(View.GONE);
		}
		titleBtn.setText(R.string.post);
		mTitleView.setText(R.string.status);
		toggleEnablePostButton();
		/*
		 * Show soft keyboard.
		 */
		InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		imm.showSoftInput(statusTxt, InputMethodManager.SHOW_IMPLICIT);
	}

	private void hideSoftKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(statusTxt.getWindowToken(), 0);
	}

	private String getStatusDefaultHint() {
		return getString(R.string.whats_up_user, Utils.getFirstName(preferences
				.getString(HikeMessengerApp.NAME_SETTING, "")));
	}

	private void postStatus() {
		final EditText statusTxt = (EditText) findViewById(R.id.status_txt);
		HikeHttpRequest hikeHttpRequest = new HikeHttpRequest("/user/status",
				RequestType.STATUS_UPDATE, new HikeHttpCallback() {

					@Override
					public void onSuccess(JSONObject response) {
						mActivityTask = new ActivityTask();

						JSONObject data = response.optJSONObject("data");

						String mappedId = data
								.optString(HikeConstants.STATUS_ID);
						String text = data
								.optString(HikeConstants.STATUS_MESSAGE);
						int moodId = data.optInt(HikeConstants.MOOD) - 1;
						int timeOfDay = data.optInt(HikeConstants.TIME_OF_DAY);
						String msisdn = preferences.getString(
								HikeMessengerApp.MSISDN_SETTING, "");
						String name = preferences.getString(
								HikeMessengerApp.NAME_SETTING, "");
						long time = (long) System.currentTimeMillis() / 1000;

						StatusMessage statusMessage = new StatusMessage(0,
								mappedId, msisdn, name, text,
								StatusMessageType.TEXT, time, moodId, timeOfDay);
						HikeConversationsDatabase.getInstance()
								.addStatusMessage(statusMessage, true);

						int unseenUserStatusCount = preferences.getInt(
								HikeMessengerApp.UNSEEN_USER_STATUS_COUNT, 0);
						Editor editor = preferences.edit();
						editor.putString(HikeMessengerApp.LAST_STATUS, text);
						editor.putInt(HikeMessengerApp.LAST_MOOD, moodId);
						editor.putInt(
								HikeMessengerApp.UNSEEN_USER_STATUS_COUNT,
								++unseenUserStatusCount);
						editor.commit();

						HikeMessengerApp.getPubSub().publish(
								HikePubSub.MY_STATUS_CHANGED, text);

						/*
						 * This would happen in the case where the user has
						 * added a self contact and received an mqtt message
						 * before saving this to the db.
						 */
						if (statusMessage.getId() != -1) {
							HikeMessengerApp.getPubSub().publish(
									HikePubSub.STATUS_MESSAGE_RECEIVED,
									statusMessage);
							HikeMessengerApp.getPubSub().publish(
									HikePubSub.TIMELINE_UPDATE_RECIEVED,
									statusMessage);
						}
						HikeMessengerApp.getPubSub().publish(
								HikePubSub.STATUS_POST_REQUEST_DONE, true);
					}

					@Override
					public void onFailure() {
						Toast.makeText(getApplicationContext(),
								R.string.update_status_fail, Toast.LENGTH_SHORT)
								.show();
						mActivityTask.hikeHTTPTask = null;
						HikeMessengerApp.getPubSub().publish(
								HikePubSub.STATUS_POST_REQUEST_DONE, false);
					}

				});
		String status = null;
		/*
		 * If the text box is empty, the we take the hint text which is a
		 * prefill for moods.
		 */
		if (TextUtils.isEmpty(statusTxt.getText())) {
			status = statusTxt.getHint().toString();
		} else {
			status = statusTxt.getText().toString();
		}

		boolean facebook = findViewById(R.id.post_fb_btn).isSelected();
		boolean twitter = findViewById(R.id.post_twitter_btn).isSelected();

		Log.d(getClass().getSimpleName(), "Status: " + status);
		JSONObject data = new JSONObject();
		try {
			data.put(HikeConstants.STATUS_MESSAGE_2, status);
			data.put(HikeConstants.FACEBOOK_STATUS, facebook);
			data.put(HikeConstants.TWITTER_STATUS, twitter);
			if (mActivityTask.moodId != -1) {
				data.put(HikeConstants.MOOD, mActivityTask.moodId + 1);
				data.put(HikeConstants.TIME_OF_DAY, getTimeOfDay());
			}
		} catch (JSONException e) {
			Log.w(getClass().getSimpleName(), "Invalid JSON", e);
		}
		Log.d(getClass().getSimpleName(), "JSON: " + data);

		hikeHttpRequest.setJSONData(data);
		mActivityTask.hikeHTTPTask = new HikeHTTPTask(null, 0);
		mActivityTask.hikeHTTPTask.execute(hikeHttpRequest);

		progressDialog = ProgressDialog.show(this, null, getResources()
				.getString(R.string.updating_status));
	}

	private void setSelectionSocialButton(boolean facebook, boolean selection) {
		View v = findViewById(facebook ? R.id.post_fb_btn
				: R.id.post_twitter_btn);
		v.setSelected(selection);
		if (!facebook) {
			if (statusTxt.length() > HikeConstants.MAX_TWITTER_POST_LENGTH) {
				Toast.makeText(getApplicationContext(),
						R.string.twitter_length_exceed, Toast.LENGTH_SHORT)
						.show();
				v.setSelected(false);
				return;
			}
			setCharCountForStatus(findViewById(R.id.char_counter),
					(EditText) findViewById(R.id.status_txt), v.isSelected());
			mActivityTask.twitterSelected = v.isSelected();
		} else {
			mActivityTask.fbSelected = v.isSelected();
		}
	}

	private void setCharCountForStatus(View charCounter, EditText statusTxt,
			boolean isSelected) {
		charCounter.setVisibility(isSelected ? View.VISIBLE : View.GONE);

		if (isSelected) {
			statusTxt
					.setFilters(new InputFilter[] { new InputFilter.LengthFilter(
							HikeConstants.MAX_TWITTER_POST_LENGTH) });
		} else {
			statusTxt.setFilters(new InputFilter[] {});
		}
	}

	private void showEmojiSelector() {
		hideSoftKeyboard();

		mActivityTask.emojiShowing = true;

		showCancelButton(false);

		emojiParent.setClickable(true);
		View categories = findViewById(R.id.emoticons_categories_container);
		View shadow = findViewById(R.id.top_shadow);

		emojiParent.setVisibility(View.VISIBLE);
		categories.setVisibility(View.GONE);
		shadow.setVisibility(View.GONE);

		ViewGroup emoticonLayout = (ViewGroup) findViewById(R.id.emoji_container);
		final ViewPager emoticonViewPager = (ViewPager) findViewById(R.id.emoticon_pager);
		final EditText statusTxt = (EditText) findViewById(R.id.status_txt);
		final TabHost tabHost = (TabHost) findViewById(android.R.id.tabhost);
		tabHost.setup();

		int whichSubcategory = 0;
		boolean isTabInitialised = tabHost.getTabWidget().getTabCount() > 0;

		if (tabHost != null && !isTabInitialised) {
			isTabInitialised = true;

			int[] tabDrawables = null;

			int offset = 0;
			int emoticonsListSize = 0;
			tabDrawables = new int[] { R.drawable.ic_recents_emo,
					EmoticonConstants.EMOJI_RES_IDS[0],
					EmoticonConstants.EMOJI_RES_IDS[109],
					EmoticonConstants.EMOJI_RES_IDS[162],
					EmoticonConstants.EMOJI_RES_IDS[294],
					EmoticonConstants.EMOJI_RES_IDS[392] };
			offset = EmoticonConstants.DEFAULT_SMILEY_RES_IDS.length;
			emoticonsListSize = EmoticonConstants.EMOJI_RES_IDS.length;

			LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
			for (int i = 0; i < tabDrawables.length; i++) {
				View tabHead = layoutInflater.inflate(
						R.layout.emoticon_tab_layout, null);
				TabSpec ts = tabHost.newTabSpec("tab" + (i + 1));

				((ImageView) tabHead.findViewById(R.id.tab_header_img))
						.setImageResource(tabDrawables[i]);
				if (i == 0) {
					tabHead.findViewById(R.id.divider_left).setVisibility(
							View.GONE);
				} else if (i == tabDrawables.length - 1) {
					tabHead.findViewById(R.id.divider_right).setVisibility(
							View.GONE);
				}
				ts.setIndicator(tabHead);
				ts.setContent(new TabContentFactory() {

					@Override
					public View createTabContent(String tag) {
						View v = new View(getApplicationContext());
						v.setMinimumWidth(0);
						v.setMinimumHeight(0);
						return v;
					}
				});
				tabHost.addTab(ts);
			}

			/*
			 * Checking whether we have a few emoticons in the recents category.
			 * If not we show the next tab emoticons.
			 */
			if (whichSubcategory == 0) {
				int startOffset = offset;
				int endOffset = startOffset + emoticonsListSize;
				int recentEmoticonsSizeReq = StatusEmojiAdapter.MAX_EMOTICONS_PER_ROW;
				int[] recentEmoticons = HikeConversationsDatabase.getInstance()
						.fetchEmoticonsOfType(EmoticonType.EMOJI, startOffset,
								endOffset, recentEmoticonsSizeReq);
				if (recentEmoticons.length < recentEmoticonsSizeReq) {
					whichSubcategory++;
				}
			}
			setRecentlyUsedTextVisibility(whichSubcategory);

			setupEmoticonLayout(EmoticonType.EMOJI, whichSubcategory,
					emoticonViewPager, statusTxt);
			tabHost.setCurrentTab(whichSubcategory);
		}

		emoticonLayout.setVisibility(View.VISIBLE);

		emoticonViewPager.setOnPageChangeListener(new OnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				tabHost.setCurrentTab(position);
				setRecentlyUsedTextVisibility(position);
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
			}

			@Override
			public void onPageScrollStateChanged(int arg0) {
			}
		});

		tabHost.setOnTabChangedListener(new OnTabChangeListener() {
			@Override
			public void onTabChanged(String tabId) {
				emoticonViewPager.setCurrentItem(tabHost.getCurrentTab());
				setRecentlyUsedTextVisibility(tabHost.getCurrentTab());
			}
		});
	}

	public void hideEmoticonSelector() {
		onBackPressed();
	}

	private void setRecentlyUsedTextVisibility(int currentPage) {
		findViewById(R.id.recent_use_head).setVisibility(
				currentPage == 0 ? View.VISIBLE : View.GONE);
	}

	private void setupEmoticonLayout(EmoticonType emoticonType,
			int whichSubcategory, ViewPager emoticonViewPager,
			EditText statusTxt) {

		StatusEmojiAdapter statusEmojiAdapter = new StatusEmojiAdapter(this,
				statusTxt);
		emoticonViewPager.setAdapter(statusEmojiAdapter);
		emoticonViewPager.invalidate();
	}

	private void showMoodSelector() {
		hideSoftKeyboard();

		mActivityTask.moodShowing = true;

		showCancelButton(true);

		moodParent.setClickable(true);
		GridView moodPager = (GridView) findViewById(R.id.mood_pager);

		moodParent.setVisibility(View.VISIBLE);

		MoodAdapter moodAdapter = new MoodAdapter(this);
		moodPager.setAdapter(moodAdapter);
		moodPager.setOnItemClickListener(moodAdapter);
	}

	public void setMood(int moodId) {
		if (moodId == -1) {
			return;
		}
		mActivityTask.moodId = moodId;

		avatar.setImageResource(EmoticonConstants.MOOD_RES_IDS[moodId]);
		avatar.setBackgroundResource(0);

		int timeOfDay = getTimeOfDay();
		String[] prefillTextArray;
		if (timeOfDay == 1) {
			prefillTextArray = getResources().getStringArray(
					R.array.mood_prefills_morning);
		} else if (timeOfDay == 2) {
			prefillTextArray = getResources().getStringArray(
					R.array.mood_prefills_day);
		} else {
			prefillTextArray = getResources().getStringArray(
					R.array.mood_prefills_night);
		}

		statusTxt
				.setHint(moodId < prefillTextArray.length ? prefillTextArray[moodId]
						: getStatusDefaultHint());

		toggleEnablePostButton();
		if (isEmojiOrMoodLayoutVisible()) {
			onBackPressed();
		}
	}

	private void showCancelButton(boolean moodLayout) {
		titleBtn.setText(R.string.cancel);
		titleBtn.setEnabled(true);
		if (moodLayout) {
			mTitleView.setText(R.string.moods);
		}
	}

	private int getTimeOfDay() {
		int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
		if (hour >= 4 && hour < 12) {
			return 1;
		} else if (hour >= 12 && hour < 20) {
			return 2;
		}
		return 3;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		HikeMessengerApp.getFacebook().authorizeCallback(requestCode,
				resultCode, data);
	}

	public void toggleEnablePostButton() {
		EditText statusTxt = (EditText) findViewById(R.id.status_txt);

		/*
		 * Enabling if the text length is > 0 or if the user has selected a mood
		 * with some prefilled text.
		 */
		titleBtn.setEnabled((mActivityTask.moodId >= 0 && mActivityTask.moodId < 11)
				|| (statusTxt.length() > 0));
	}

	@Override
	public void onEventReceived(final String type, Object object) {
		if (HikePubSub.SOCIAL_AUTH_COMPLETED.equals(type)
				|| HikePubSub.SOCIAL_AUTH_FAILED.equals(type)) {
			final boolean facebook = (Boolean) object;
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					setSelectionSocialButton(facebook,
							HikePubSub.SOCIAL_AUTH_COMPLETED.equals(type));
				}
			});
		} else if (HikePubSub.STATUS_POST_REQUEST_DONE.equals(type)) {
			final boolean statusPosted = (Boolean) object;
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (progressDialog != null) {
						progressDialog.dismiss();
						progressDialog = null;
					}
					if (statusPosted) {
						finish();
					}
				}
			});
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (progressDialog != null) {
			progressDialog.dismiss();
			progressDialog = null;
		}
	}
}