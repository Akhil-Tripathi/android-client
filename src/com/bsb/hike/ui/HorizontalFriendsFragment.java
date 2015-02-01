package com.bsb.hike.ui;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.NUXConstants;
import com.bsb.hike.R;
import com.bsb.hike.BitmapModule.BitmapUtils;
import com.bsb.hike.models.ContactInfo;
import com.bsb.hike.models.NuxSelectFriends;
import com.bsb.hike.modules.contactmgr.ContactManager;
import com.bsb.hike.utils.Logger;
import com.bsb.hike.utils.NUXManager;
import com.bsb.hike.utils.Utils;


public class HorizontalFriendsFragment extends Fragment implements OnClickListener{
	
	private Map<String, View> viewMap;
	private final String emptyTag="emptyView";

	private LinearLayout viewStack;
	private int maxShowListCount;
	private int preSelectedCount;
	private HorizontalScrollView hsc; 
	private NuxSelectFriends selectFriends;
	private TextView sectionDisplayMessage;
	private TextView nxtBtn;
	private ImageView backBtn;
	private HashSet<String> contactsDisplayed;
	
	private void changeLayoutParams(){
		WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
    	Display display = wm.getDefaultDisplay();
    	Logger.d("UmangX", "message : " + display.getWidth()+ " "+ viewStack.getWidth());
    	if(viewStack.getWidth() < display.getWidth()){
    		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT);
    		params.gravity = Gravity.CENTER;
    		viewStack.setLayoutParams(params);
    	}
    	else {
    		Logger.d("UmangX", "" + viewStack.getChildAt(0).getWidth() + "  " + NUXManager.getInstance().getCountLockedContacts());
    		scrollHorizontalView(contactsDisplayed.size() - 1, viewStack.getChildAt(0).getWidth());
    	}
	}
	
    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle savedInstanceState) {

        View v =  inf.inflate(R.layout.display_selected_friends, parent, false); 
        String selectedFriendsString = getActivity().getIntent().getStringExtra("selected_friends");

        viewStack = (LinearLayout) v.findViewById(R.id.horizontalView);
        hsc = (HorizontalScrollView) v.findViewById(R.id.scrollView);
		sectionDisplayMessage = (TextView) v.findViewById(R.id.nux_header_selection_text);
        nxtBtn = (TextView) v.findViewById(R.id.nux_next_selection_button);
        backBtn = (ImageView) v.findViewById(R.id.back_button);
        nxtBtn.setOnClickListener(this);
        backBtn.setOnClickListener(this);
        
        viewMap = new LinkedHashMap<String, View>();
        contactsDisplayed = new HashSet<String>();
		NUXManager nm = NUXManager.getInstance();
		selectFriends = nm.getNuxSelectFriendsPojo();
		preSelectedCount = nm.getCountLockedContacts() + nm.getCountUnlockedContacts();
		
		viewStack.post(new Runnable() {	
			@Override
			public void run() {
				changeLayoutParams();			
			}
		});
		//First Time Nux
		if(nm.getCurrentState() == NUXConstants.NUX_NEW || nm.getCurrentState()==NUXConstants.NUX_SKIPPED)
		{
			maxShowListCount = nm.getNuxTaskDetailsPojo().getMin();
		}
		// invite more nux 
		else if(nm.getCurrentState() == NUXConstants.NUX_IS_ACTIVE)
		{
			maxShowListCount = nm.getNuxTaskDetailsPojo().getMax();
			//scrollHorizontalView(0, viewStack.getChildAt(0).getWidth());
		}
		
		//this only appears for custom message screen
		if (getActivity() instanceof NuxSendCustomMessageActivity) 
		{

			showNextButton(true);
			nxtBtn.setText(nm.getNuxCustomMessagePojo().getButText());
			//only when jumping from Compose Chat Activity
			if(!TextUtils.isEmpty(selectedFriendsString))
			{
				String[] arrmsisdn = selectedFriendsString.split(NUXConstants.STRING_SPLIT_SEPERATOR);
				contactsDisplayed.addAll(Arrays.asList(arrmsisdn));
				contactsDisplayed.removeAll(nm.getLockedContacts());
			}
			//remind me button 
			else 
			{
				contactsDisplayed.addAll(nm.getLockedContacts());
			}
			for (String msisdn : contactsDisplayed) 
			{
				addContactView(msisdn, viewStack.getChildCount());		
			}
		} else if (getActivity() instanceof ComposeChatActivity) {
			nxtBtn.setText(selectFriends.getButText());
			contactsDisplayed.addAll(nm.getLockedContacts());
			for (String msisdn : contactsDisplayed) {
				addContactView(msisdn, viewStack.getChildCount());
			}
			for (int i = 0; i < maxShowListCount - preSelectedCount; i++) 
				addEmptyView();
			changeDisplayString(0);
		}
		return v;
	}
    
    
    
    private void addEmptyView(){
    	View emptyView = getLayoutInflater(null).inflate(R.layout.friends_horizontal_item,null);
    	ImageView iv = (ImageView ) emptyView.findViewById(R.id.profile_image);
		iv.setScaleType(ScaleType.CENTER_INSIDE);
		iv.setBackgroundResource(R.drawable.avatar_empty);
		iv.setImageResource(R.drawable.ic_question_mark);
    	emptyView.setTag(emptyTag);
    	viewStack.addView(emptyView);
    }
    
    private void addContactView(String msisdn, int index){
    	if(!viewMap.containsKey(msisdn)){
    		View contactView = getLayoutInflater(null).inflate(R.layout.friends_horizontal_item,null);
    		contactView.setTag(msisdn);
        	TextView tv = (TextView)contactView.findViewById(R.id.msisdn);
        	ImageView iv = (ImageView ) contactView.findViewById(R.id.profile_image);
        	
			if(ContactManager.getInstance().getIcon(msisdn, true) ==null){
				iv.setScaleType(ScaleType.CENTER_INSIDE);
				iv.setBackgroundResource(BitmapUtils.getDefaultAvatarResourceId(msisdn, true));
				iv.setImageResource(R.drawable.ic_profile);
			}
			else
			{
				iv.setImageDrawable(ContactManager.getInstance().getIcon(msisdn, true));
			}
        	ContactInfo contactInfo = ContactManager.getInstance().getContact(msisdn);
        	
        	if(contactInfo != null)
        		tv.setText(contactInfo.getFirstNameAndSurname());
        	else
        		tv.setText(msisdn);
        	viewStack.addView(contactView, index);
        	viewMap.put(msisdn,contactView);
    	}
    	
    }
   
    public boolean removeView(ContactInfo contactInfo){
    	
    	if(NUXManager.getInstance().getLockedContacts().contains(contactInfo.getMsisdn()) || NUXManager.getInstance().getUnlockedContacts().contains(contactInfo.getMsisdn()))
    		return false;
    	
    	int filledCount = 0; 
    	int index  = 0;
    	View replaceView = null;
    	for (int i = 0; i < viewStack.getChildCount(); i++) {
            View v = viewStack.getChildAt(i);
            if(!v.getTag().toString().contains(emptyTag)){
            	if(contactInfo.getMsisdn().equals(v.getTag().toString()))
            		replaceView = v; index = i;
            	filledCount++;
            }
            
        }
    	//if(count  == 5) return false;
    	changeDisplayString(filledCount - 1 - preSelectedCount);
		viewStack.removeView(replaceView); 	
		viewMap.remove(contactInfo.getMsisdn());
		scrollHorizontalView(index -1 , replaceView.getWidth());
		addEmptyView();

        return true;
    }
  
    
    private void showNextButton(boolean show){
    	if(show){
    		nxtBtn.setTextColor(getResources().getColor(R.color.blue_hike));
    	} else {
    		nxtBtn.setTextColor(getResources().getColor(R.color.light_gray_hike));
    	}
    	nxtBtn.setEnabled(show);
    }

    private void changeDisplayString(int selectionCount){
    	
    	if(selectionCount >= maxShowListCount - preSelectedCount){
    		showNextButton(true);
        	sectionDisplayMessage.setText(selectFriends.getTitle3());
    	} else if(selectionCount > 0 && selectionCount < maxShowListCount - preSelectedCount){
    		showNextButton(NUXManager.getInstance().getCurrentState() == NUXConstants.NUX_IS_ACTIVE);
        	sectionDisplayMessage.setText(String.format(selectFriends.getTitle2(), maxShowListCount - selectionCount - preSelectedCount));
    	} else if(selectionCount <= 0){
    		showNextButton(false);
    		sectionDisplayMessage.setText(String.format(selectFriends.getSectionTitle(), maxShowListCount - selectionCount - preSelectedCount));
    	}

    }
    
    private void scrollHorizontalView(int count, int width){
    	hsc.scrollTo(count*width, 0);
    }

    public boolean addView(ContactInfo contactInfo){
		
    	if(viewMap.containsKey(contactInfo.getMsisdn())){
    		return false;
    	}
    	int index = 0,emptyCount = 0;
    	View replaceView = null;
    	for (int i = 0; i < viewStack.getChildCount(); i++) {
            View v = viewStack.getChildAt(i);
            if(v.getTag().toString().contains(emptyTag)){
            	if(emptyCount == 0){
            		index = i; replaceView = v;
            	}
            	emptyCount++;
            }
            
        }
    	//count here means total non selected contacts
    	if(emptyCount == 0) return false;
    	changeDisplayString((maxShowListCount - preSelectedCount) - (emptyCount - 1));
    	addContactView(contactInfo.getMsisdn(), index);
    	scrollHorizontalView(maxShowListCount - emptyCount - 1, replaceView.getWidth());
    	viewStack.removeView(replaceView);
		return true;
    }
    
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		if ((NUXManager.getInstance().getCurrentState() == NUXConstants.NUX_KILLED))
		{
			KillActivity();
		}
	}
    
	@Override
	public void onClick(View v) 
	{
		switch(v.getId()){
		
			case R.id.back_button:
				getActivity().finish();
				break;
				
			case R.id.nux_next_selection_button:
	
				NUXManager nm = NUXManager.getInstance();
				
			if ((nm.getCurrentState() == NUXConstants.NUX_KILLED))
			{
				KillActivity();
				return;
			}

			if (getActivity() instanceof ComposeChatActivity)
			{
				HashSet<String> contactsNux = new HashSet<String>(viewMap.keySet());
				nm.startNuxCustomMessage(contactsNux.toString().replace("[", "").replace("]", ""), getActivity());

			}
			else if (getActivity() instanceof NuxSendCustomMessageActivity)
			{
				nm.sendMessage(contactsDisplayed, ((NuxSendCustomMessageActivity) getActivity()).getCustomMessage());
				
				Logger.d("UmangX","displayed : "+contactsDisplayed.toString());
				contactsDisplayed.removeAll(nm.getLockedContacts());
				if(!contactsDisplayed.isEmpty()){
					nm.sendMsisdnListToServer(contactsDisplayed);
					nm.saveNUXContact(contactsDisplayed);
				}
				nm.setCurrentState(NUXConstants.NUX_IS_ACTIVE);
				KillActivity();
			}
				break;
		}
		
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if (NUXManager.getInstance().getCurrentState() == NUXConstants.NUX_KILLED)
			KillActivity();
	}

	private void KillActivity()
	{
		Intent in = (Utils.getHomeActivityIntent(getActivity()));
		in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

		getActivity().startActivity(in);
		getActivity().finish();
	}

}
