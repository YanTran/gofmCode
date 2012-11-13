package com.gofm.radio;

import java.io.IOException;

import org.npr.android.news.SongParserClient;
import org.npr.android.news.StreamProxy;

import android.media.AudioManager;
import android.util.Log;
 
/**
 * The following code was derived from http://www.speakingcode.com/2012/02/22/
 * creating-a-streaming-audio-app-for-android-with-android-media-mediaplayer-
 * android-media-audiomanager-and-android-app-service/
 * 
 * It is covered under  Creative Commons Attribution-ShareAlike 3.0 Unported License 
 * (http://creativecommons.org/licenses/by-sa/3.0/).
 */
/**
 * A subclass of android.media.MediaPlayer which provides methods for
 * state-management, data-source management, etc.
 * @author rootlicker http://speakingcode.com
 */
public class StatefulMediaPlayer extends android.media.MediaPlayer implements SongParserClient{
    /**
     * Set of states for StatefulMediaPlayer:<br>
     * EMPTY, CREATED, PREPARED, STARTED, PAUSED, STOPPED, ERROR
     * @author rootlicker
     */
    public enum MPStates {
        EMPTY, CREATED, PREPARED, STARTED, PAUSED, STOPPED, ERROR
    }
 
    private MPStates mState;
    private StreamStation mStreamStation;
 
    public StreamStation getStreamStation() {
        return mStreamStation;
    }
    private SongParserClient mClient;
    
    public void setClient(SongParserClient client)
    {
    	mClient = client;
    }
    
    /**
     * Sets a StatefulMediaPlayer's data source as the provided StreamStation
     * @param streamStation the StreamStation to set as the data source
     */
    public void setStreamStation(StreamStation streamStation) {
        this.mStreamStation = streamStation;
        try {
        	if (proxy == null) {
      	      proxy = new StreamProxy();
      	      proxy.init();
      	      proxy.start();
      	    }
      	    String proxyUrl = String.format("http://127.0.0.1:%d/%s", proxy.getPort(), mStreamStation.getStationUrl());
      	    playUrl = proxyUrl;

      	
          setDataSource(playUrl);
           // setDataSource(streamStation.getStationUrl());
            setState(MPStates.CREATED);
        }
        catch (Exception e) {
            Log.e("StatefulMediaPlayer", "setDataSource failed");
            setState(MPStates.ERROR);
        }
    }
 
    /**
     * Instantiates a StatefulMediaPlayer object.
     */
    public StatefulMediaPlayer() {
        super();
        setState(MPStates.CREATED);
    }
 
    private StreamProxy proxy = null;
    private String playUrl = new String("");
    /**
     * Instantiates a StatefulMediaPlayer object with the Audio Stream Type
     * set to STREAM_MUSIC and the provided StreamStation's URL as the data source.
     * @param streamStation The StreamStation to use as the data source
     */
    public StatefulMediaPlayer(StreamStation streamStation) {
        super();
        this.setAudioStreamType(AudioManager.STREAM_MUSIC);
        this.mStreamStation = streamStation;
        try {
        	if (proxy == null) {
        	      proxy = new StreamProxy();
        	      proxy.setClient(this);
        	      proxy.init();
        	      proxy.start();
        	    }
        	    String proxyUrl = String.format("http://127.0.0.1:%d/%s", proxy.getPort(), mStreamStation.getStationUrl());
        	    playUrl = proxyUrl;

        	
            setDataSource(playUrl);
            setState(MPStates.CREATED);
        }
        catch (Exception e) {
            Log.e("StatefulMediaPlayer", "setDataSourceFailed");
            setState(MPStates.ERROR);
        }
    }
 
    @Override
    public void reset() {
        super.reset();
        this.mState = MPStates.EMPTY;
    }
 
    @Override
    public void start() {
        super.start();
        setState(MPStates.STARTED);
    }
 
    @Override
    public void pause() {
 
        super.pause();
        setState(MPStates.PAUSED);
 
    }
 
    @Override
    public void stop() {
    	proxy.stop();
        super.stop();
        
        setState(MPStates.STOPPED);
    }
 
    @Override
    public void release() {
        super.release();
        setState(MPStates.EMPTY);
    }
 
    @Override
    public void prepare() throws IOException, IllegalStateException {
        super.prepare();
        setState(MPStates.PREPARED);
    }
 
    @Override
    public void prepareAsync() throws IllegalStateException {
    	
        super.prepareAsync();
        setState(MPStates.PREPARED);
    }
 
    public MPStates getState() {
        return mState;
    }
 
    /**
     * @param state the state to set
     */
    public void setState(MPStates state) {
        this.mState = state;
    }
 
    public boolean isCreated() {
        return (mState == MPStates.CREATED);
    }
 
    public boolean isEmpty() {
        return (mState == MPStates.EMPTY);
    }
 
    public boolean isStopped() {
        return (mState == MPStates.STOPPED);
    }
 
    public boolean isStarted() {
        return (mState == MPStates.STARTED || this.isPlaying());
    }
 
    public boolean isPaused() {
        return (mState == MPStates.PAUSED);
    }
 
    public boolean isPrepared() {
        return (mState == MPStates.PREPARED);
    }

	public void onPlayingSong(String title, String artist) {

	}
	
	public void onGotMetaData(String metaString)
	{
		mClient.onGotMetaData(metaString);
	}

	public void onSongParserError() {
		// TODO Auto-generated method stub
		mClient.onSongParserError();
	}
}