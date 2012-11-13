package com.gofm.radio;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.npr.android.news.SongParserClient;
import org.xml.sax.SAXException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.RemoteControlClient;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

import com.amazon.advertising.api.sample.SignedRequestsHelper;
import com.gofm.radio.StatefulMediaPlayer.MPStates;
import com.sibyl.AmazonParser;
/**
 * The following code was derived from http://www.speakingcode.com/2012/02/22/
 * creating-a-streaming-audio-app-for-android-with-android-media-mediaplayer-
 * android-media-audiomanager-and-android-app-service/
 * 
 * It is covered under  Creative Commons Attribution-ShareAlike 3.0 Unported License 
 * (http://creativecommons.org/licenses/by-sa/3.0/).
 */
/**
 * An extension of android.app.Service class which provides access to a
 * StatefulMediaPlayer.<br>
 * 
 * @author rootlicker http://speakingcode.com
 * @see com.speakingcode.android.media.mediaplayer.StatefulMediaPlayer
 */

@TargetApi(16)
public class MediaPlayerService extends Service implements
		OnBufferingUpdateListener, OnInfoListener, OnPreparedListener,
		OnErrorListener, SongParserClient {

	private static final String LOG_TAG = MediaPlayerService.class.getName();
	private StatefulMediaPlayer mMediaPlayer = new StatefulMediaPlayer();
	private final Binder mBinder = new MediaPlayerBinder();
	private IMediaPlayerServiceClient mClient;
	private static MediaPlayerService self;

	private String mAWSID;
	private String mAWSKey;
	
	
	private SignedRequestsHelper helper;
	Bitmap albumBitmap;


	private static final String ENDPOINT = "ecs.amazonaws.com";

	/**
	 * A class for clients binding to this service. The client will be passed an
	 * object of this class via its onServiceConnected(ComponentName, IBinder)
	 * callback.
	 */
	public class MediaPlayerBinder extends Binder {
		/**
		 * Returns the instance of this service for a client to make method
		 * calls on it.
		 * 
		 * @return the instance of this service.
		 */
		public MediaPlayerService getService() {
			return MediaPlayerService.this;
		}

	}

	/**
	 * Returns the contained StatefulMediaPlayer
	 * 
	 * @return
	 */
	public StatefulMediaPlayer getMediaPlayer() {
		return mMediaPlayer;
	}

	private boolean getAudioFocus() {

		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		int result = am.requestAudioFocus(afChangeListener,
		// Use the music stream.
				AudioManager.STREAM_MUSIC,
				// Request permanent focus.
				AudioManager.AUDIOFOCUS_GAIN);

		return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
	}

	public class IncomingCallReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();

			if (null == bundle)
				return;

			Log.i("IncomingCallReceiver", bundle.toString());

			String state = bundle.getString(TelephonyManager.EXTRA_STATE);

			Log.i("IncomingCallReceiver", "State: " + state);

			if (state.equalsIgnoreCase(TelephonyManager.EXTRA_STATE_RINGING)) {

				stopMediaPlayer();
				mClient.onServiceStopped();

			}
		}

	}

	public class OutgoingCallReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();

			if (null == bundle)
				return;


			stopMediaPlayer();
			mClient.onServiceStopped();
		}
	}

	private final OutgoingCallReceiver oR = new OutgoingCallReceiver();

	private final IncomingCallReceiver iR = new IncomingCallReceiver();

	private WifiLock mWifiLock = null;

	RemoteControlClient myRemoteControlClient = null;
	PendingIntent mediaPendingIntent = null;
	private final MPRemoteControlEventReceiver rR = new MPRemoteControlEventReceiver();
	ComponentName myEventReceiver = null; //new ComponentName(getPackageName(), MPRemoteControlEventRecevier.class.getName());
	@SuppressLint("NewApi")
	private void commonSetup() {
		
		 mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");
		 
		 mWifiLock.acquire();
		
		 
			IntentFilter rFilter = new IntentFilter();
			rFilter.addAction(Intent.ACTION_MEDIA_BUTTON);
			rFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

			// ---register the receiver---
			registerReceiver(rR, rFilter);
			
			myEventReceiver = new ComponentName(getPackageName(), MPRemoteControlEventReceiver.class.getName());

			 AudioManager myAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			 myAudioManager.registerMediaButtonEventReceiver(myEventReceiver);
			 // build the PendingIntent for the remote control client
			 Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
			 mediaButtonIntent.setComponent(myEventReceiver);
			 mediaPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
			 // create and register the remote control client
			 if (VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				 myRemoteControlClient = new RemoteControlClient(mediaPendingIntent);
				 
				 myAudioManager.registerRemoteControlClient(myRemoteControlClient);
				 myRemoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_STOP | RemoteControlClient.FLAG_KEY_MEDIA_PLAY);
				 myRemoteControlClient.setPlaybackState( RemoteControlClient.PLAYSTATE_STOPPED);
			 }
			
	 
		mMediaPlayer.setClient(this);
		mMediaPlayer.setWakeMode(getApplicationContext(),
				PowerManager.PARTIAL_WAKE_LOCK);
		mMediaPlayer.setOnBufferingUpdateListener(this);
		mMediaPlayer.setOnInfoListener(this);
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.prepareAsync();

		IntentFilter oiFilter = new IntentFilter();
		oiFilter.addAction("android.intent.action.NEW_OUTGOING_CALL");

		// ---register the receiver---
		registerReceiver(oR, oiFilter);

		IntentFilter iiFilter = new IntentFilter();
		iiFilter.addAction("android.intent.action.PHONE_STATE");

		// ---register the receiver---
		registerReceiver(iR, iiFilter);

		if (helper == null) {
			try {
				helper = SignedRequestsHelper.getInstance(ENDPOINT,
						mAWSID, mAWSKey);
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}
		

		 
	}
	
	public static class MPRemoteControlEventReceiver extends BroadcastReceiver
	{

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			Log.d("", "got remote control intent");
			if(intent.getAction() == Intent.ACTION_MEDIA_BUTTON)
			{
				KeyEvent key = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
	            if(key == null || key.getAction() == KeyEvent.ACTION_DOWN) {
	                /*int keycode = key.getKeyCode();
	                if(keycode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keycode == KeyEvent.KEYCODE_MEDIA_STOP || keycode == KeyEvent.KEYCODE_MEDIA_PAUSE) 
	                {*/
	            	self.stopMediaPlayer();
					self.clearNotification();	
	                self.mClient.onServiceStopped();
	                	
	                //}
	            }
			}
		}
		
	}

	/**
	 * Initializes a StatefulMediaPlayer for streaming playback of the provided
	 * StreamStation
	 * 
	 * @param station
	 *            The StreamStation representing the station to play
	 */
	public boolean initializePlayer(StreamStation station) {
		boolean result = getAudioFocus();

		if (result == true) {
			mClient.onInitializePlayerStart("Connecting...");
			mMediaPlayer = new StatefulMediaPlayer(station);

			commonSetup();
		}
		return result;
	}

	/**
	 * Initializes a StatefulMediaPlayer for streaming playback of the provided
	 * stream url
	 * 
	 * @param streamUrl
	 *            The URL of the stream to play.
	 */
	public boolean initializePlayer(String streamUrl) {

		boolean result = getAudioFocus();

		if (result == true) {

			mMediaPlayer = new StatefulMediaPlayer();
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			try {
				mMediaPlayer.setDataSource(streamUrl);
			} catch (Exception e) {
				Log.e("MediaPlayerService", "error setting data source");
				mMediaPlayer.setState(MPStates.ERROR);
			}

			commonSetup();
		}
		return result;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	public void onBufferingUpdate(MediaPlayer player, int percent) {

	}

	private void releaseWifiLock() {
		if (mWifiLock != null && mWifiLock.isHeld()) {
			mWifiLock.release();
			mWifiLock = null;
		}
	}

	public boolean onError(MediaPlayer player, int what, int extra) {
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		am.abandonAudioFocus(afChangeListener);
		mMediaPlayer.reset();
		mClient.onError();
		releaseWifiLock();
		return true;
	}

	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {

		}
		return false;
	}
	
	public void onMetaData(StatefulMediaPlayer player)
	{
		
	}

	@SuppressLint("NewApi")
	public void onPrepared(MediaPlayer player) {
		

		startMediaPlayer();
		mClient.onInitializePlayerSuccess();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		self = this;
		return START_STICKY;
	}

	/**
	 * Pauses the contained StatefulMediaPlayer
	 */
	public void pauseMediaPlayer() {
		Log.d("MediaPlayerService", "pauseMediaPlayer() called");
		mMediaPlayer.pause();
		stopForeground(true);

	}

	/**
	 * Sets the client using this service.
	 * 
	 * @param client
	 *            The client of this service, which implements the
	 *            IMediaPlayerServiceClient interface
	 */
	public void setClient(IMediaPlayerServiceClient client) {
		this.mClient = client;
	}

	/**
	 * Starts the contained StatefulMediaPlayer and foregrounds the service to
	 * support persisted background playback.
	 */
	public void startMediaPlayer() {


		Log.d("MediaPlayerService", "startMediaPlayer() called");
		mMediaPlayer.start();
		if(myRemoteControlClient != null)
			myRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);

	}

	/**
	 * Stops the contained StatefulMediaPlayer.
	 */
	public void stopMediaPlayer() {
		// stopForeground(true);
		if(mMediaPlayer != null)
		{
		if(mMediaPlayer.isPlaying() || mMediaPlayer.isStarted() || mMediaPlayer.isPrepared())
			mMediaPlayer.stop();
			
		}
		mMediaPlayer.release();
		mMediaPlayer = null;
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		am.abandonAudioFocus(afChangeListener);
		am.unregisterMediaButtonEventReceiver(myEventReceiver);
		if(myRemoteControlClient != null)
			am.unregisterRemoteControlClient(myRemoteControlClient);
		unregisterReceiver(oR);
		unregisterReceiver(iR);
		unregisterReceiver(rR);
		releaseWifiLock();
		mCurrentArtist = "";
		mCurrentTitle = "";
	}

	public void resetMediaPlayer() {
		// stopForeground(true);
		mMediaPlayer.reset();
	}

	OnAudioFocusChangeListener afChangeListener = new OnAudioFocusChangeListener() {
		public void onAudioFocusChange(int focusChange) {

			if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
				Log.d("", "lost audio focus transient");
				stopMediaPlayer();
				mClient.onServiceStopped();

			} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {


				if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
					// Lower the volume
					mMediaPlayer.setVolume(0.1f, 0.1f);
				}
			} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {

				if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
					// Raise it back to normal
					mMediaPlayer.setVolume(1.0f, 1.0f);
					AudioManager myAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
					myAudioManager.registerMediaButtonEventReceiver(myEventReceiver);
					if(myRemoteControlClient != null)
						myAudioManager.registerRemoteControlClient(myRemoteControlClient);
					
					mMediaPlayer.setWakeMode(getApplicationContext(),
							PowerManager.PARTIAL_WAKE_LOCK);
				}
			} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
				Log.d("", "lost audio focus ");
				stopMediaPlayer();
				mClient.onServiceStopped();
			}
		}
	};

	String mCurrentArtist = new String("");;
	String mCurrentTitle = new String("");;
	private Notification notification;
	private Intent notificationIntent;
	private PendingIntent contentIntent;
	private static final int NOWPLAYING_ID = 1;

	Bitmap scaledGoFMIcon = null;
	@SuppressLint("NewApi")
	public void setNotification() {
		if ((mMediaPlayer != null && this.mMediaPlayer.isPlaying() == false)
				|| mMediaPlayer == null) {
			return;
		}
		Resources res = getResources();
		
		
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		int icon;
		if (VERSION.SDK_INT >=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			icon = R.drawable.gofmblack;
		}
		else
		{
			icon = R.drawable.ic_stat_gofm;
		}
		
		long when = System.currentTimeMillis();
		

		Context context = getApplicationContext();
		if (/*notification == null || */notificationIntent == null) {
			notificationIntent = new Intent((MainActivity) mClient,
					MainActivity.class);
			notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);

			
			CharSequence tickerText = "";
			contentIntent = PendingIntent.getActivity((MainActivity) mClient,
					0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
			

		} else {
			contentIntent = PendingIntent.getActivity((MainActivity) mClient,
					0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		}

		CharSequence contentTitle = mCurrentTitle;// "Currently Playing:";
		CharSequence contentText = mCurrentArtist;
		if (VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			

			Notification.Builder builder = new Notification.Builder(context);
			builder.setContentIntent(contentIntent).setSmallIcon(icon)	
					.setWhen(when)
					.setAutoCancel(false)
					.setContentTitle(mCurrentTitle)
					.setContentText(mCurrentArtist);
			
			if(albumBitmap != null)
			{
				
				int width = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
				int height = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
				
				Bitmap scaledAlbumArt = Bitmap.createScaledBitmap(albumBitmap, width, height, true);
				
				builder.setLargeIcon(scaledAlbumArt);
				if(scaledGoFMIcon == null)
				{
					if (VERSION.SDK_INT >=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					{
					scaledGoFMIcon = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(
						getResources(), icon), width, height, true);
					}
					else
					{
						scaledGoFMIcon = Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(), icon));
					}
				}
				builder.setStyle(new Notification.BigPictureStyle().bigLargeIcon(scaledGoFMIcon).bigPicture(albumBitmap).setSummaryText(mCurrentArtist));
			}
			builder.addAction(
	                R.drawable.ic_media_stop,
	                "Stop",
	                mediaPendingIntent);
			notification = builder.build();
		} else {
			if(notification == null)
			{
				notification = new Notification(icon, "", when);
			}
			notification.setLatestEventInfo(context, contentTitle, contentText,
					contentIntent);

		}

		notification.flags |= Notification.FLAG_NO_CLEAR;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.audioStreamType = AudioManager.STREAM_MUSIC;
		
		if (mClient != null && mClient.isPaused() == true) {
			//mNotificationManager.notify(NOWPLAYING_ID, notification);
			startForeground(NOWPLAYING_ID, notification);
		}
		
		if (VERSION.SDK_INT >=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			// in case it got unregistered by a competing audio app
			AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			am.registerMediaButtonEventReceiver(myEventReceiver);
			if(myRemoteControlClient != null)
				am.registerRemoteControlClient(myRemoteControlClient);

			myRemoteControlClient
					.editMetadata(false)
					.putString(MediaMetadataRetriever.METADATA_KEY_TITLE,
							mCurrentTitle)
					.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
							mCurrentArtist)
					.putBitmap(
							RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK,
							albumBitmap).apply();
		}
		
		
		mMediaPlayer.setWakeMode(getApplicationContext(),
				PowerManager.PARTIAL_WAKE_LOCK);
		
	}

	public void clearNotification() {
		stopForeground(true);

	}

	private static final String QUERY_AMAZON = "Service=AWSECommerceService"
			+ "&Version=2009-03-31"
			+ "&AssociateTag=%22"
			+ "&Operation=ItemSearch"
			+ "&SearchIndex=MP3Downloads"
			+ "&ResponseGroup=Images";

	protected class AmazonThread extends Thread
	{
		private String mq;
		void setQueryString(String query)
		{
			mq = query;
		}
		public void run () {
		      AmazonFunction(mq);
		   }
		
		private void AmazonFunction(String q)
		{
			String answer = null;

			// TODO Auto-generated method stub
			// amazon answer xml parser
			AmazonParser ap = new AmazonParser();
			// request to amazon website
			try {
				SAXParserFactory.newInstance().newSAXParser()
						.parse(new URL(q).openStream(), ap);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// retrieve images from answer
			answer = ap.getResult();
			if (answer != null && !answer.isEmpty())
				createImageIcon(answer);
			else
				albumBitmap = null;

			mClient.onGotArtWork(albumBitmap);
			setNotification();
		}
	}
	
	protected class AmazonTask extends AsyncTask<String, String, String> {

		@Override
		protected String doInBackground(String... params) {
			String answer = null;
			if (params.length == 0)
				return answer;
			String q = params[0];
			// TODO Auto-generated method stub
			// amazon answer xml parser
			AmazonParser ap = new AmazonParser();
			// request to amazon website
			try {
				SAXParserFactory.newInstance().newSAXParser()
						.parse(new URL(q).openStream(), ap);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// retrieve images from answer
			answer = ap.getResult();
			if (answer != null && !answer.isEmpty())
				createImageIcon(answer);
			else
				albumBitmap = null;
			return answer;
		}

		@Override
		protected void onPostExecute(String result) {

			mClient.onGotArtWork(albumBitmap);
			setNotification();


		}

	}

	@SuppressLint("NewApi")
	public void getAlbumArtImageIcon(String artist, String song)
			throws Exception {

		if(artist.contains("GoFM") || artist.contains("Go FM") || artist.contains("SRR Liner"))
		{
			return;
		}

		String q = QUERY_AMAZON + "&Keywords=" + song + " " + artist;

		String request = helper.sign(q);
		Log.d(LOG_TAG, request );
				

		//AmazonFunction(request);
		
		
		AmazonThread aT = new AmazonThread();
		aT.setQueryString(request);
		aT.start();


	}

	private void createImageIcon(String url) {

		try {
			Log.d("MediaPlayerService",url);
			albumBitmap = (BitmapFactory.decodeStream(new URL(url)
					.openConnection().getInputStream()));
			
			setNotification();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	class MetaDataParser extends Thread
	{
		private String metaString;
		public void setData(String data)
		{
			metaString = data;
		}
		
		public void run()
		{
			Map<String, String> metaData = StreamMetaParser.parseMetadata(metaString);
			String title = StreamMetaParser.getTitle(metaData);
			String artist = new String("");
			try {
				artist = StreamMetaParser.getArtist(metaData);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
		
			
			// TODO Auto-generated method stub
			if(title != null && title.compareTo(mCurrentTitle) != 0 && artist.compareTo(mCurrentArtist) != 0 )
			{
				mCurrentTitle = title;
				mCurrentArtist = artist;
				mClient.onPlayingSong(title, artist);
				try {
					getAlbumArtImageIcon(artist, title);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				setNotification();
			}
		}
		
	};
	
	public void onGotMetaData(String metaData)
	{
		MetaDataParser p = new MetaDataParser();
		p.setData(metaData);
		p.start();
	}
	
	public void onPlayingSong(String title, String artist) {

		
	}

	public void onSongParserError() {
		// TODO Auto-generated method stub
		if(mMediaPlayer != null && mMediaPlayer.isPlaying())
			this.stopMediaPlayer();
		clearNotification();
		mClient.onError();
	}

	public void setAWSCredentials(String awsAccessKeyId, String awsSecretKey) {
		
		mAWSID = awsAccessKeyId;
		mAWSKey = awsSecretKey;
	}

	

}