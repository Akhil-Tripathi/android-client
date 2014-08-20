package com.bsb.hike.notifications;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.R;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.db.HikeUserDatabase;
import com.bsb.hike.models.ContactInfo;
import com.bsb.hike.models.ConvMessage;
import com.bsb.hike.models.GroupConversation;
import com.bsb.hike.models.ConvMessage.ParticipantInfoState;
import com.bsb.hike.models.HikeFile.HikeFileType;
import com.bsb.hike.utils.Logger;
import com.bsb.hike.utils.SmileyParser;
import com.bsb.hike.utils.Utils;

public class HikeNotificationUtils
{
	/**
	 * Utility method to get a "msisdn/name - message" preview from ConvMsg.
	 * 
	 * @param context
	 * @param db
	 * @param convMsg
	 * @return
	 */
	public static Pair<String, String> getNotificationPreview(Context context, HikeUserDatabase db, ConvMessage convMsg)
	{

		final String msisdn = convMsg.getMsisdn();

		// Check whether the message contains any files
		String message = (!convMsg.isFileTransferMessage()) ? convMsg.getMessage() : HikeFileType.getFileTypeMessage(context, convMsg.getMetadata().getHikeFiles().get(0)
				.getHikeFileType(), convMsg.isSent());

		ContactInfo contactInfo;
		if (convMsg.isGroupChat())
		{
			Logger.d("HikeNotificationStack2", "GroupName is " + convMsg.getConversation().getLabel());
			contactInfo = new ContactInfo(convMsg.getMsisdn(), convMsg.getMsisdn(), convMsg.getConversation().getLabel(), convMsg.getMsisdn());
		}
		else
		{
			contactInfo = db.getContactInfoFromMSISDN(convMsg.getMsisdn(), false);
		}

		if (TextUtils.isEmpty(message)
				&& (convMsg.getParticipantInfoState() == ParticipantInfoState.USER_JOIN || convMsg.getParticipantInfoState() == ParticipantInfoState.CHAT_BACKGROUND))
		{
			if (convMsg.getParticipantInfoState() == ParticipantInfoState.USER_JOIN)
			{
				message = String.format(context.getString(convMsg.getMetadata().isOldUser() ? R.string.user_back_on_hike : R.string.joined_hike_new), contactInfo.getFirstName());
			}
			else
			{
				message = context.getString(R.string.chat_bg_changed, contactInfo.getFirstName());
			}
		}

		/*
		 * Jellybean has added support for emojis so we don't need to add a '*' to replace them
		 */
		if (Build.VERSION.SDK_INT < 16)
		{
			// Replace emojis with a '*'
			message = SmileyParser.getInstance().replaceEmojiWithCharacter(message, "*");
		}

		String key = (contactInfo != null && !TextUtils.isEmpty(contactInfo.getName())) ? contactInfo.getName() : msisdn;
		// For showing the name of the contact that sent the message in a group
		// chat
		if (convMsg.isGroupChat() && !TextUtils.isEmpty(convMsg.getGroupParticipantMsisdn()) && convMsg.getParticipantInfoState() == ParticipantInfoState.NO_INFO)
		{

			GroupConversation gConv = (GroupConversation) convMsg.getConversation();

			ContactInfo participant = gConv.getGroupParticipant(convMsg.getGroupParticipantMsisdn()).getContactInfo();

			key = participant.getName();
			if (TextUtils.isEmpty(key))
			{
				key = participant.getMsisdn();
			}

			boolean isPin = false;

			if (convMsg.getMessageType() == HikeConstants.MESSAGE_TYPE.TEXT_PIN)
				isPin = true;

			if (isPin)
			{
				message = key + " " + context.getString(R.string.pin_notif_text) + HikeConstants.SEPARATOR + message;
			}
			else
			{
				message = key + HikeConstants.SEPARATOR + message;
			}
			key = gConv.getLabel();
		}

		return new Pair<String, String>(message, key);
	}

	public static String getNameForMsisdn(Context context, HikeUserDatabase db, HikeConversationsDatabase convDb, String argMsisdn)
	{
		String name = argMsisdn;
		if (Utils.isGroupConversation(argMsisdn))
		{
			name = convDb.getGroupName(argMsisdn);
		}
		else
		{
			name = db.getContactInfoFromMSISDN(argMsisdn, false).getNameOrMsisdn();
		}
		return name;
	}
}
