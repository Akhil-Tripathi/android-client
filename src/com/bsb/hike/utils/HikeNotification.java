package com.bsb.hike.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.R;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.db.HikeUserDatabase;
import com.bsb.hike.models.ContactInfo;
import com.bsb.hike.models.ConvMessage;
import com.bsb.hike.models.ConvMessage.ParticipantInfoState;
import com.bsb.hike.models.HikeFile;
import com.bsb.hike.models.HikeFile.HikeFileType;
import com.bsb.hike.models.StatusMessage;
import com.bsb.hike.models.StatusMessage.StatusMessageType;
import com.bsb.hike.models.Sticker;
import com.bsb.hike.models.utils.IconCacheManager;
import com.bsb.hike.ui.ChatThread;
import com.bsb.hike.ui.HomeActivity;

public class HikeNotification {
	public static final int HIKE_NOTIFICATION = 0;
	public static final int BATCH_SU_NOTIFICATION_ID = 9876;
	private static final long MIN_TIME_BETWEEN_NOTIFICATIONS = 5 * 1000;
	private static final String SEPERATOR = " ";

	private Context context;

	private NotificationManager notificationManager;
	private long lastNotificationTime;
	private SharedPreferences sharedPreferences;

	public HikeNotification(Context context) {
		this.context = context;
		this.notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		this.sharedPreferences = context.getSharedPreferences(
				HikeMessengerApp.STATUS_NOTIFICATION_SETTING, 0);
	}

	public void notifyMessage(ContactInfo contactInfo, ConvMessage convMsg) {
		String msisdn = convMsg.getMsisdn();
		// we are using the MSISDN now to group the notifications
		int notificationId = msisdn.hashCode();

		String message = (!convMsg.isFileTransferMessage()) ? convMsg
				.getMessage() : HikeFileType.getFileTypeMessage(context,
				convMsg.getMetadata().getHikeFiles().get(0).getHikeFileType(),
				convMsg.isSent());
		// Message will be empty for type 'uj' when the conversation does not
		// exist
		if (TextUtils.isEmpty(message)
				&& convMsg.getParticipantInfoState() == ParticipantInfoState.USER_JOIN) {
			message = String.format(
					context.getString(R.string.joined_hike_new),
					contactInfo.getFirstName());
		}
		long timestamp = convMsg.getTimestamp();

		String key = (contactInfo != null && !TextUtils.isEmpty(contactInfo
				.getName())) ? contactInfo.getName() : msisdn;

		// For showing the name of the contact that sent the message in a group
		// chat
		if (convMsg.isGroupChat()
				&& !TextUtils.isEmpty(convMsg.getGroupParticipantMsisdn())
				&& convMsg.getParticipantInfoState() == ParticipantInfoState.NO_INFO) {
			HikeUserDatabase hUDB = HikeUserDatabase.getInstance();
			ContactInfo participant = hUDB.getContactInfoFromMSISDN(
					convMsg.getGroupParticipantMsisdn(), false);

			if (TextUtils.isEmpty(participant.getName())) {
				HikeConversationsDatabase hCDB = HikeConversationsDatabase
						.getInstance();
				participant.setName(hCDB.getParticipantName(msisdn,
						convMsg.getGroupParticipantMsisdn()));
			}
			message = participant.getFirstName() + HikeConstants.SEPARATOR
					+ message;
		}

		int icon = R.drawable.ic_contact_logo;

		/*
		 * Jellybean has added support for emojis so we don't need to add a '*'
		 * to replace them
		 */
		if (Build.VERSION.SDK_INT < 16) {
			// Replace emojis with a '*'
			message = SmileyParser.getInstance().replaceEmojiWithCharacter(
					message, "*");
		}

		Spanned text = Html.fromHtml(String.format("<bold>%1$s</bold>: %2$s",
				key, message));

		//we've got to invoke the chat thread  from here with the respective users
		Intent notificationIntent = new Intent(context, ChatThread.class);
		if (contactInfo.getName()!= null) {
			notificationIntent.putExtra(HikeConstants.Extras.NAME,
					contactInfo.getName());
		}
		notificationIntent.putExtra(HikeConstants.Extras.MSISDN, contactInfo.getMsisdn()); 

		/*
		 * notifications appear to be cached, and their .equals doesn't check
		 * 'Extra's. In order to prevent the wrong intent being fired, set a
		 * data field that's unique to the conversation we want to open.
		 * http://groups
		 * .google.com/group/android-developers/browse_thread/thread
		 * /e61ec1e8d88ea94d/1fe953564bd11609?#1fe953564bd11609
		 */

		notificationIntent.setData((Uri.parse("custom://" + notificationId)));

		notificationIntent.putExtra(HikeConstants.Extras.MSISDN, msisdn);
		if (contactInfo != null) {
			if (contactInfo.getName() != null) {
				notificationIntent.putExtra(HikeConstants.Extras.NAME,
						contactInfo.getName());
			}
		}

		if (convMsg.isStickerMessage() || convMsg.isFileTransferMessage()) {
			// big picture messages ! intercept !
			pushBigPictureMessageNotifications(notificationIntent, contactInfo,
					convMsg);
		} else
			showNotification(notificationIntent, icon, timestamp,
					notificationId, text, key, message, msisdn); // regular text
		// messages
	}

	public void notifyFavorite(ContactInfo contactInfo) {
		int notificationId = contactInfo.getMsisdn().hashCode();

		String msisdn = contactInfo.getMsisdn();

		long timeStamp = System.currentTimeMillis() / 1000;

		Intent notificationIntent = getHomeActivityIntent(0);
		notificationIntent.setData((Uri.parse("custom://" + notificationId)));

		int icon = R.drawable.ic_contact_logo;

		String key = (contactInfo != null && !TextUtils.isEmpty(contactInfo
				.getName())) ? contactInfo.getName() : msisdn;

		String message = context
				.getString(R.string.add_as_friend_notification_line);

		Spanned text = Html.fromHtml(context.getString(
				R.string.add_as_friend_notification, key));

		showNotification(notificationIntent, icon, timeStamp, notificationId,
				text, key, message, msisdn);
		addNotificationId(notificationId);
	}

	public void notifyStatusMessage(StatusMessage statusMessage) {
		/*
		 * We only proceed if the current status preference value is 0 which
		 * denotes that the user wants immediate notifications. Else we simply
		 * return
		 */
		if (PreferenceManager.getDefaultSharedPreferences(this.context).getInt(
				HikeConstants.STATUS_PREF, 0) != 0) {
			return;
		}
		int notificationId = statusMessage.getMsisdn().hashCode();

		long timeStamp = statusMessage.getTimeStamp();

		Intent notificationIntent = getHomeActivityIntent(0);
		notificationIntent.setData((Uri.parse("custom://" + notificationId)));

		int icon = R.drawable.ic_contact_logo;

		String key = statusMessage.getNotNullName();

		String message = null;
		String text = null;
		if (statusMessage.getStatusMessageType() == StatusMessageType.TEXT) {
			message = context.getString(R.string.status_text_notification, "\""
					+ statusMessage.getText() + "\"");
			/*
			 * Jellybean has added support for emojis so we don't need to add a
			 * '*' to replace them
			 */
			if (Build.VERSION.SDK_INT < 16) {
				// Replace emojis with a '*'
				message = SmileyParser.getInstance().replaceEmojiWithCharacter(
						message, "*");
			}
			text = key + " " + message;
		}  else if (statusMessage.getStatusMessageType() == StatusMessageType.FRIEND_REQUEST_ACCEPTED) {
			message = context.getString(R.string.confirmed_friend_2, key);
			text = message;
		} else {
			/*
			 * We don't know how to display this type. Just return.
			 */
			return;
		}

		showNotification(notificationIntent, icon, timeStamp, notificationId,
				text, key, message, statusMessage.getMsisdn());
		addNotificationId(notificationId);
	}

	public void notifyBatchUpdate(String header, String message) {
		long timeStamp = System.currentTimeMillis() / 1000;

		int notificationId = (int) timeStamp;

		Intent notificationIntent = getHomeActivityIntent(0);
		notificationIntent.setData((Uri.parse("custom://" + notificationId)));

		int icon = R.drawable.ic_contact_logo;

		String key = header;

		String text = message;

		showNotification(notificationIntent, icon, timeStamp, notificationId,
				text, key, message, null); // TODO: change this.
		addNotificationId(notificationId);
	}

	private Intent getHomeActivityIntent(int tabIndex) {
		Intent intent = new Intent(context, HomeActivity.class);
		intent.putExtra(HikeConstants.Extras.TAB_INDEX, tabIndex);

		return intent;
	}

	private void addNotificationId(int id) {
		String ids = sharedPreferences.getString(HikeMessengerApp.STATUS_IDS,
				"");

		ids += Integer.toString(id) + SEPERATOR;

		Editor editor = sharedPreferences.edit();
		editor.putString(HikeMessengerApp.STATUS_IDS, ids);
		editor.commit();
	}

	public void cancelAllStatusNotifications() {
		String ids = sharedPreferences.getString(HikeMessengerApp.STATUS_IDS,
				"");
		String[] idArray = ids.split(SEPERATOR);

		for (String id : idArray) {
			if (TextUtils.isEmpty(id.trim())) {
				continue;
			}
			notificationManager.cancel(Integer.parseInt(id));
		}

		Editor editor = sharedPreferences.edit();
		editor.remove(HikeMessengerApp.STATUS_IDS);
		editor.commit();
	}

	private void showNotification(Intent notificationIntent, int icon,
			long timestamp, int notificationId, CharSequence text, String key,
			String message, String msisdn) {

		boolean shouldNotPlayNotification = (System.currentTimeMillis() - lastNotificationTime) < MIN_TIME_BETWEEN_NOTIFICATIONS;

		SharedPreferences preferenceManager = PreferenceManager
				.getDefaultSharedPreferences(this.context);

		int playSound = preferenceManager.getBoolean(HikeConstants.SOUND_PREF,
				true) && !shouldNotPlayNotification ? Notification.DEFAULT_SOUND
				: 0;

		int vibrate = preferenceManager.getBoolean(HikeConstants.VIBRATE_PREF,
				true) && !shouldNotPlayNotification ? Notification.DEFAULT_VIBRATE
				: 0;

		boolean playNativeJingle = preferenceManager.getBoolean(
				HikeConstants.NATIVE_JINGLE_PREF, true);

		boolean led = preferenceManager
				.getBoolean(HikeConstants.LED_PREF, true);

		int smallIconId = R.drawable.ic_contact_logo;
		final Drawable avatarDrawable = IconCacheManager.getInstance()
				.getIconForMSISDN(msisdn);
		Bitmap avatarBitmap = Utils.drawableToBitmap(avatarDrawable);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				context).setContentTitle(key).setSmallIcon(smallIconId)
				.setLargeIcon(avatarBitmap).setContentText(message)
				.setAutoCancel(true)
				.setTicker(text).setDefaults(vibrate);

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		if (playNativeJingle && playSound != 0) {
			mBuilder.setSound(Uri.parse("android.resource://"
					+ context.getPackageName() + "/" + R.raw.v1));
		} else if (playSound != 0) {
			mBuilder.setDefaults(mBuilder.getNotification().defaults
					| playSound);
		}

		if (led) {
			mBuilder.setLights(Color.BLUE, 300, 1000);
		}
		stackBuilder.addNextIntent(notificationIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);

		notificationManager.notify(notificationId, mBuilder.getNotification());

		lastNotificationTime = shouldNotPlayNotification ? lastNotificationTime
				: System.currentTimeMillis();
	}

	public void pushBigPictureStatusNotifications(String[] profileStruct) {

		if (PreferenceManager.getDefaultSharedPreferences(this.context).getInt(
				HikeConstants.STATUS_PREF, 0) != 0) {
			return;
		}
		boolean shouldNotPlayNotification = (System.currentTimeMillis() - lastNotificationTime) < MIN_TIME_BETWEEN_NOTIFICATIONS;

		SharedPreferences preferenceManager = PreferenceManager
				.getDefaultSharedPreferences(this.context);

		int playSound = preferenceManager.getBoolean(HikeConstants.SOUND_PREF,
				true) && !shouldNotPlayNotification ? Notification.DEFAULT_SOUND
				: 0;

		int vibrate = preferenceManager.getBoolean(HikeConstants.VIBRATE_PREF,
				true) && !shouldNotPlayNotification ? Notification.DEFAULT_VIBRATE
				: 0;

		boolean playNativeJingle = preferenceManager.getBoolean(
				HikeConstants.NATIVE_JINGLE_PREF, true);

		boolean led = preferenceManager
				.getBoolean(HikeConstants.LED_PREF, true);
		
		String title = TextUtils.isEmpty(profileStruct[2])?profileStruct[1]:profileStruct[2];  // TODO:: replace the struct
		
		String message = context
					.getString(R.string.status_profile_pic_notification);
		String	text = title + " " + message;
		
		int smallIconId = R.drawable.ic_contact_logo;
		Bitmap bigPictureImage = BitmapFactory.decodeFile(profileStruct[0]);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
				.setContentTitle(title)
				.setSmallIcon(smallIconId)
				.setAutoCancel(true)
				.setLargeIcon(bigPictureImage)
				.setTicker(text)
				.setContentText(text);

		NotificationCompat.BigPictureStyle bigPicStyle = new NotificationCompat.BigPictureStyle();
		bigPicStyle.bigPicture(bigPictureImage);
		bigPicStyle.setBigContentTitle(title);
		mBuilder.setStyle(bigPicStyle);

		Intent resultIntent = getHomeActivityIntent(0);
		resultIntent.setData((Uri.parse("custom://"
				+ profileStruct[1].toString().hashCode())));
		resultIntent.putExtra(HikeConstants.Extras.MSISDN,
				profileStruct[1].toString());
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addNextIntent(resultIntent);

		if (playNativeJingle && playSound != 0) {
			mBuilder.setSound(Uri.parse("android.resource://"
					+ context.getPackageName() + "/" + R.raw.v1));
		} else if (playSound != 0) {
			mBuilder.setDefaults(mBuilder.getNotification().defaults
					| playSound);
		}

		if (led) {
			mBuilder.setLights(Color.BLUE, 300, 1000);
		}

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);

		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(profileStruct[1].hashCode(),
				mBuilder.build());
	}

	public void pushBigPictureMessageNotifications(Intent notificationIntent,
			ContactInfo contactInfo, ConvMessage convMessage) {

		boolean isSticker = convMessage.isStickerMessage();
		HikeFile hikeFile = null;
		Sticker sticker = null;
		Bitmap bigPictureImage = null;
		String filePath = "";
		String msisdn = convMessage.getMsisdn();
		String key = (contactInfo != null && !TextUtils.isEmpty(contactInfo
				.getName())) ? contactInfo.getName() : msisdn;
		int smallIconId = R.drawable.ic_contact_logo;
		
		final Drawable avatarDrawable = IconCacheManager.getInstance()
				.getIconForMSISDN(contactInfo.getMsisdn());
		Bitmap avatarBitmap = Utils.drawableToBitmap(avatarDrawable);
		int notificationId = convMessage.getMsisdn().hashCode(); // group the
		String maskedText = Utils.getMessageDisplayText(convMessage, context);
		// conversation

		Spanned text = Html.fromHtml(String.format("<bold>%1$s</bold>: %2$s",
				key, maskedText));
		
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				context).setContentTitle(key).setSmallIcon(smallIconId)
				.setAutoCancel(true)
				.setLargeIcon(avatarBitmap)
				.setTicker(text)
				.setContentText(maskedText);

		NotificationCompat.BigPictureStyle bigPicStyle = new NotificationCompat.BigPictureStyle();

		bigPicStyle.setBigContentTitle(key);
		mBuilder.setStyle(bigPicStyle);
		boolean doesExist = false;
		// check if this is a sticker or a file and populate the big picture
		// accordingly
		if (isSticker) {
			sticker = convMessage.getMetadata().getSticker();

			/*
			 * If this is the first category, then the sticker are a part of the
			 * app bundle itself
			 */
			if (sticker.getStickerIndex() != -1) {

				int resourceId = EmoticonConstants.LOCAL_STICKER_RES_IDS[sticker
						.getStickerIndex()];
				if (resourceId > 0) {
					Drawable dr = context.getResources()
							.getDrawable(resourceId);
					bigPictureImage = Utils.drawableToBitmap(dr);
					doesExist = true;
				}

			} else {
				filePath = sticker.getStickerPath(context);
				if (!TextUtils.isEmpty(filePath)) {
					bigPictureImage = BitmapFactory.decodeFile(filePath);
					if (bigPictureImage != null)
						doesExist = true;
				}
			}

		} else {
			hikeFile = convMessage.getMetadata().getHikeFiles().get(0);
			filePath = hikeFile.getFilePath(); // check
			bigPictureImage = BitmapFactory.decodeFile(filePath);
			if (bigPictureImage != null)
				doesExist = true;
		}

		if (doesExist) {
			// set the big picture image
			bigPicStyle.bigPicture(bigPictureImage);
		}

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addNextIntent(notificationIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);

		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(notificationId, mBuilder.build());

	}
}
