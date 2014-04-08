package com.bsb.hike.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.R;
import com.bsb.hike.models.HikeFile.HikeFileType;
import com.bsb.hike.smartImageLoader.FileImageLoader;
import com.bsb.hike.tasks.InitiateMultiFileTransferTask;
import com.bsb.hike.utils.HikeAppStateBaseFragmentActivity;
import com.bsb.hike.utils.Logger;
import com.bsb.hike.utils.Utils;

public class FileSelectActivity extends HikeAppStateBaseFragmentActivity implements OnScrollListener, HikePubSub.Listener
{

	public static abstract interface DocumentSelectActivityDelegate
	{
		public void didSelectFile(FileSelectActivity activity, String path, String name, String ext, long size);
	}

	private ListView listView;

	private ListAdapter listAdapter;

	private File currentDir;

	private TextView emptyView;

	private ArrayList<ListItem> items = new ArrayList<ListItem>();

	private boolean receiverRegistered = false;

	private ArrayList<HistoryEntry> history = new ArrayList<HistoryEntry>();

	private long sizeLimit = 1024 * 1024 * 1024;

	public DocumentSelectActivityDelegate delegate;

	private boolean multiSelectMode;

	private Map<String, ListItem> selectedFileMap;

	private String currentTitle;

	private InitiateMultiFileTransferTask fileTransferTask;

	private ProgressDialog progressDialog;

	private class ListItem
	{
		int icon;

		String title;

		String subtitle = "";

		String extension = "";

		String mimeType;

		boolean showThumbnail;

		File file;
	}

	private class HistoryEntry
	{
		int scrollItem, scrollOffset;

		File dir;

		String title;
	}

	private BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Runnable r = new Runnable()
			{
				public void run()
				{
					if (currentDir == null)
					{
						listRoots();
					}
					else
					{
						listFiles(currentDir);
					}
				}
			};
			if (Intent.ACTION_MEDIA_UNMOUNTED.equals(intent.getAction()))
			{
				listView.postDelayed(r, 1000);
			}
			else
			{
				r.run();
			}
		}
	};

	private int previousFirstVisibleItem;

	private int velocity;

	private long previousEventTime;

	private TextView title;

	private TextView multiSelectTitle;

	private TextView subText;

	@Override
	public void onDestroy()
	{
		try
		{
			if (receiverRegistered)
			{
				unregisterReceiver(receiver);
			}
		}
		catch (Exception e)
		{
			Logger.e(getClass().getSimpleName(), "Exception while unregistering receiver", e);
		}
		if (progressDialog != null)
		{
			progressDialog.dismiss();
			progressDialog = null;
		}
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.MULTI_FILE_TASK_FINISHED, this);
		super.onDestroy();
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (!receiverRegistered)
		{
			receiverRegistered = true;
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
			filter.addAction(Intent.ACTION_MEDIA_CHECKING);
			filter.addAction(Intent.ACTION_MEDIA_EJECT);
			filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
			filter.addAction(Intent.ACTION_MEDIA_NOFS);
			filter.addAction(Intent.ACTION_MEDIA_REMOVED);
			filter.addAction(Intent.ACTION_MEDIA_SHARED);
			filter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
			filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
			filter.addDataScheme("file");
			registerReceiver(receiver, filter);
		}

		setContentView(R.layout.file_select_layout);

		Object object = getLastCustomNonConfigurationInstance();

		if (object instanceof InitiateMultiFileTransferTask)
		{
			progressDialog = ProgressDialog.show(this, null, getResources().getString(R.string.multi_file_creation));
		}

		selectedFileMap = new HashMap<String, ListItem>();

		listAdapter = new ListAdapter(this);
		emptyView = (TextView) findViewById(R.id.search_empty_view);
		listView = (ListView) findViewById(R.id.file_list);
		listView.setEmptyView(emptyView);
		listView.setAdapter(listAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l)
			{
				ListItem item = items.get(i);
				File file = item.file;
				if (multiSelectMode)
				{
					if (!file.isDirectory())
					{
						if(file.length() == 0)
						{
							Toast.makeText(FileSelectActivity.this, R.string.cannot_select_zero_byte_file, Toast.LENGTH_SHORT).show();
							return;
						}
						if (selectedFileMap.containsKey(item.title))
						{
							selectedFileMap.remove(item.title);
							if (selectedFileMap.isEmpty())
							{
								setupActionBar(currentTitle);
								multiSelectMode = false;
							}
							else
							{
								setMultiSelectTitle();
							}
						}
						else
						{
							selectedFileMap.put(item.title, item);
							setMultiSelectTitle();
						}
						listAdapter.notifyDataSetChanged();
					}
				}
				else
				{
					if (file.isDirectory())
					{
						HistoryEntry he = new HistoryEntry();
						he.scrollItem = listView.getFirstVisiblePosition();
						he.scrollOffset = listView.getChildAt(0).getTop();
						he.dir = currentDir;
						he.title = title.getText().toString();
						if (!listFiles(file))
						{
							return;
						}
						history.add(he);
						setTitle(item.title);
						listView.setSelection(0);
					}
					else
					{
						if (!file.canRead())
						{
							showErrorBox(getString(R.string.access_error));
							return;
						}
						if (sizeLimit != 0)
						{
							if (file.length() > sizeLimit)
							{
								showErrorBox(getString(R.string.file_upload_limit, formatFileSize(sizeLimit)));
								return;
							}
						}
						if (file.length() == 0)
						{
							return;
						}
						Intent intent = new Intent(FileSelectActivity.this, ChatThread.class);
						intent.putExtra(HikeConstants.Extras.MSISDN, getIntent().getStringExtra(HikeConstants.MSISDN));
						intent.putExtra(HikeConstants.Extras.FILE_PATH, file.getAbsolutePath());
						intent.putExtra(HikeConstants.Extras.FILE_TYPE, item.mimeType);
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						startActivity(intent);
					}
				}
			}
		});

		listView.setOnItemLongClickListener(new OnItemLongClickListener()
		{

			@Override
			public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id)
			{
				ListItem listItem = items.get(position);

				if (listItem.file.isDirectory())
				{
					return false;
				}

				if (!multiSelectMode)
				{
					multiSelectMode = true;
					setupMultiSelectActionBar();
				}

				selectedFileMap.put(listItem.title, listItem);

				listAdapter.notifyDataSetChanged();

				setMultiSelectTitle();

				return true;
			}

		});

		listRoots();
		setupActionBar(getString(R.string.select_file));

		HikeMessengerApp.getPubSub().addListener(HikePubSub.MULTI_FILE_TASK_FINISHED, this);
	}

	private void setMultiSelectTitle()
	{
		if (multiSelectTitle == null)
		{
			return;
		}
		multiSelectTitle.setText(getString(R.string.gallery_num_selected, selectedFileMap.size()));
	}

	private void setupActionBar(String titleString)
	{
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);

		View actionBarView = LayoutInflater.from(this).inflate(R.layout.compose_action_bar, null);

		View backContainer = actionBarView.findViewById(R.id.back);

		title = (TextView) actionBarView.findViewById(R.id.title);
		subText = (TextView) actionBarView.findViewById(R.id.subtext);

		setTitle(titleString);
		currentTitle = titleString;

		backContainer.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				onBackPressed();
			}
		});

		actionBar.setCustomView(actionBarView);
	}

	private void setupMultiSelectActionBar()
	{
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);

		View actionBarView = LayoutInflater.from(this).inflate(R.layout.chat_theme_action_bar, null);

		View sendBtn = actionBarView.findViewById(R.id.done_container);
		View closeBtn = actionBarView.findViewById(R.id.close_action_mode);
		ViewGroup closeContainer = (ViewGroup) actionBarView.findViewById(R.id.close_container);

		multiSelectTitle = (TextView) actionBarView.findViewById(R.id.title);
		multiSelectTitle.setText(getString(R.string.gallery_num_selected, 1));

		sendBtn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				ArrayList<Pair<String, String>> fileDetails = new ArrayList<Pair<String, String>>(selectedFileMap.size());
				for (Entry<String, ListItem> fileDetailEntry : selectedFileMap.entrySet())
				{
					ListItem listItem = fileDetailEntry.getValue();

					String filePath = listItem.file.getAbsolutePath();
					String fileType = listItem.mimeType;

					fileDetails.add(new Pair<String, String>(filePath, fileType));
				}
				String msisdn = getIntent().getStringExtra(HikeConstants.Extras.MSISDN);
				boolean onHike = getIntent().getBooleanExtra(HikeConstants.Extras.ON_HIKE, true);

				fileTransferTask = new InitiateMultiFileTransferTask(getApplicationContext(), fileDetails, msisdn, onHike);
				Utils.executeAsyncTask(fileTransferTask);

				progressDialog = ProgressDialog.show(FileSelectActivity.this, null, getResources().getString(R.string.multi_file_creation));
			}
		});

		closeContainer.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				onBackPressed();
			}
		});

		actionBar.setCustomView(actionBarView);

		Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_left_noalpha);
		slideIn.setInterpolator(new AccelerateDecelerateInterpolator());
		slideIn.setDuration(200);
		closeBtn.startAnimation(slideIn);
		sendBtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in));
	}

	@Override
	public Object onRetainCustomNonConfigurationInstance()
	{
		if (fileTransferTask != null)
		{
			return fileTransferTask;
		}
		else
		{
			return null;
		}
	}

	private void setTitle(String titleString)
	{
		title.setText(titleString);

		if (!history.isEmpty())
		{
			subText.setVisibility(View.VISIBLE);
			subText.setText(R.string.tap_hold_multi_select);
		}
		else
		{
			subText.setVisibility(View.GONE);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	public void onBackPressed()
	{
		if (multiSelectMode)
		{
			selectedFileMap.clear();
			listAdapter.notifyDataSetChanged();

			setupActionBar(currentTitle);
			multiSelectMode = false;
		}
		else if (history.size() > 0)
		{
			HistoryEntry he = history.remove(history.size() - 1);
			setTitle(he.title);
			if (he.dir != null)
			{
				listFiles(he.dir);
			}
			else
			{
				listRoots();
			}
			listView.setSelectionFromTop(he.scrollItem, he.scrollOffset);
		}
		else
		{
			super.onBackPressed();
		}
	}

	private boolean listFiles(File dir)
	{
		if (!dir.canRead())
		{
			if (dir.getAbsolutePath().startsWith(Environment.getExternalStorageDirectory().toString()) || dir.getAbsolutePath().startsWith("/sdcard")
					|| dir.getAbsolutePath().startsWith("/mnt/sdcard"))
			{
				if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && !Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY))
				{
					currentDir = dir;
					items.clear();
					String state = Environment.getExternalStorageState();
					if (Environment.MEDIA_SHARED.equals(state))
					{
						emptyView.setText(R.string.usb_active);
					}
					else
					{
						emptyView.setText(R.string.not_mounted);
					}
					listAdapter.notifyDataSetChanged();
					return true;
				}
			}
			showErrorBox(getString(R.string.access_error));
			return false;
		}
		emptyView.setText(R.string.no_files);
		File[] files = null;
		try
		{
			files = dir.listFiles();
		}
		catch (Exception e)
		{
			showErrorBox(e.getLocalizedMessage());
			return false;
		}
		if (files == null)
		{
			showErrorBox(getString(R.string.unknown_error));
			return false;
		}
		currentDir = dir;
		items.clear();
		Arrays.sort(files, new Comparator<File>()
		{
			@Override
			public int compare(File lhs, File rhs)
			{
				if (lhs.isDirectory() != rhs.isDirectory())
				{
					return lhs.isDirectory() ? -1 : 1;
				}
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});
		for (File file : files)
		{
			if (file.getName().startsWith("."))
			{
				continue;
			}
			ListItem item = new ListItem();
			item.title = file.getName();
			item.file = file;
			if (file.isDirectory())
			{
				item.icon = R.drawable.ic_folder;
			}
			else
			{
				String extension = Utils.getFileExtension(file.getName());
				item.extension = TextUtils.isEmpty(extension) ? "?" : extension;
				item.subtitle = formatFileSize(file.length());
				item.mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.extension);

				if (!TextUtils.isEmpty(item.mimeType) && HikeFileType.IMAGE == HikeFileType.fromString(item.mimeType))
				{
					item.showThumbnail = true;
				}
			}
			items.add(item);
		}
		listAdapter.notifyDataSetChanged();
		return true;
	}

	private void showErrorBox(String error)
	{
		new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(error).setPositiveButton(R.string.ok, null).show();
	}

	private void listRoots()
	{
		currentDir = null;
		items.clear();
		String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
		ListItem ext = new ListItem();
		ext.title = getString(!Utils.hasGingerbread() || Environment.isExternalStorageRemovable() ? R.string.sd_card : R.string.internal_storage);
		ext.icon = R.drawable.ic_folder;
		ext.subtitle = getRootSubtitle(extStorage);
		ext.file = Environment.getExternalStorageDirectory();
		items.add(ext);
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
			String line;
			HashMap<String, ArrayList<String>> aliases = new HashMap<String, ArrayList<String>>();
			ArrayList<String> result = new ArrayList<String>();
			String extDevice = null;
			while ((line = reader.readLine()) != null)
			{
				if ((!line.contains("/mnt") && !line.contains("/storage") && !line.contains("/sdcard")) || line.contains("asec") || line.contains("tmpfs") || line.contains("none"))
				{
					continue;
				}
				String[] info = line.split(" ");
				if (!aliases.containsKey(info[0]))
				{
					aliases.put(info[0], new ArrayList<String>());
				}
				aliases.get(info[0]).add(info[1]);
				if (info[1].equals(extStorage))
				{
					extDevice = info[0];
				}
				result.add(info[1]);
			}
			reader.close();
			if (extDevice != null)
			{
				result.removeAll(aliases.get(extDevice));
				for (String path : result)
				{
					try
					{
						boolean isSd = path.toLowerCase().contains("sd");
						ListItem item = new ListItem();
						item.title = getString(isSd ? R.string.sd_card : R.string.external_storage);
						item.icon = R.drawable.ic_folder;
						item.subtitle = getRootSubtitle(path);
						item.file = new File(path);
						items.add(item);
					}
					catch (Exception e)
					{
						Logger.e(getClass().getSimpleName(), "Exception while showing root", e);
					}
				}
			}
		}
		catch (Exception e)
		{
			Logger.e(getClass().getSimpleName(), "Exception while showing root", e);
		}
		ListItem fs = new ListItem();
		fs.title = "/";
		fs.subtitle = getString(R.string.system_root);
		fs.icon = R.drawable.ic_folder;
		fs.file = new File("/");
		items.add(fs);
		listAdapter.notifyDataSetChanged();
	}

	private String getRootSubtitle(String path)
	{
		StatFs stat = new StatFs(path);
		long total = (long) stat.getBlockCount() * (long) stat.getBlockSize();
		long free = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
		if (total == 0)
		{
			return "";
		}
		return getString(R.string.free_of_total, formatFileSize(free), formatFileSize(total));
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
		if (previousFirstVisibleItem != firstVisibleItem)
		{
			long currTime = System.currentTimeMillis();
			long timeToScrollOneElement = currTime - previousEventTime;
			velocity = (int) (((double) 1 / timeToScrollOneElement) * 1000);

			previousFirstVisibleItem = firstVisibleItem;
			previousEventTime = currTime;
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		listAdapter.setIsListFlinging(velocity > HikeConstants.MAX_VELOCITY_FOR_LOADING_IMAGES && scrollState == OnScrollListener.SCROLL_STATE_FLING);
	}

	private class ListAdapter extends BaseAdapter
	{
		private LayoutInflater inflater;

		private FileImageLoader fileImageLoader;

		private boolean isListFlinging;

		public ListAdapter(Context context)
		{
			inflater = LayoutInflater.from(context);
			int size = getResources().getDimensionPixelSize(R.dimen.file_thumbnail_size);
			fileImageLoader = new FileImageLoader(size, size);
		}

		public void setIsListFlinging(boolean b)
		{
			boolean notify = b != isListFlinging;

			isListFlinging = b;
			fileImageLoader.setPauseWork(isListFlinging);

			if (notify && !isListFlinging)
			{
				notifyDataSetChanged();
			}
		}

		@Override
		public int getCount()
		{
			return items.size();
		}

		@Override
		public Object getItem(int position)
		{
			return items.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return 0;
		}

		public int getViewTypeCount()
		{
			return 2;
		}

		public int getItemViewType(int pos)
		{
			return items.get(pos).subtitle.length() > 0 ? 0 : 1;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View v = convertView;
			ListItem item = items.get(position);
			if (v == null)
			{
				v = inflater.inflate(R.layout.file_item, parent, false);
				if (item.subtitle.length() == 0)
				{
					v.findViewById(R.id.file_item_info).setVisibility(View.GONE);
				}
			}
			View selectorView = v.findViewById(R.id.selector_view);

			TextView typeTextView = (TextView) v.findViewById(R.id.file_item_type);
			((TextView) v.findViewById(R.id.file_item_title)).setText(item.title);

			((TextView) v.findViewById(R.id.file_item_info)).setText(item.subtitle);
			ImageView imageView = (ImageView) v.findViewById(R.id.file_item_thumb);
			if (item.showThumbnail)
			{
				fileImageLoader.loadImage(FileImageLoader.FILE_KEY_PREFIX + item.file.getAbsolutePath(), imageView, isListFlinging);
				imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				imageView.setVisibility(View.VISIBLE);

				typeTextView.setText(item.extension.toUpperCase().substring(0, Math.min(item.extension.length(), 4)));
				typeTextView.setVisibility(View.VISIBLE);
			}
			else if (item.icon != 0)
			{
				imageView.setImageResource(item.icon);
				imageView.setScaleType(ImageView.ScaleType.CENTER);
				imageView.setVisibility(View.VISIBLE);
				typeTextView.setVisibility(View.INVISIBLE);
			}
			else
			{
				typeTextView.setText(item.extension.toUpperCase().substring(0, Math.min(item.extension.length(), 4)));
				imageView.setVisibility(View.GONE);
				typeTextView.setVisibility(View.VISIBLE);
			}

			selectorView.setSelected(selectedFileMap.containsKey(item.title));

			return v;
		}
	}

	private String formatFileSize(long size)
	{
		if (size < 1024)
		{
			return String.format("%d B", size);
		}
		else if (size < 1024 * 1024)
		{
			return String.format("%.1f KB", size / 1024.0f);
		}
		else if (size < 1024 * 1024 * 1024)
		{
			return String.format("%.1f MB", size / 1024.0f / 1024.0f);
		}
		else
		{
			return String.format("%.1f GB", size / 1024.0f / 1024.0f / 1024.0f);
		}
	}

	@Override
	public void onEventReceived(String type, Object object)
	{
		super.onEventReceived(type, object);

		if (HikePubSub.MULTI_FILE_TASK_FINISHED.equals(type))
		{
			fileTransferTask = null;

			runOnUiThread(new Runnable()
			{

				@Override
				public void run()
				{
					Intent intent = new Intent(FileSelectActivity.this, ChatThread.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(HikeConstants.Extras.MSISDN, getIntent().getStringExtra(HikeConstants.Extras.MSISDN));
					startActivity(intent);

					if (progressDialog != null)
					{
						progressDialog.dismiss();
						progressDialog = null;
					}
				}
			});
		}
	}
}
