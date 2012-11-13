package com.gofm.radio;

import android.graphics.Bitmap;
/**
 * The following code was derived from http://www.speakingcode.com/2012/02/22/
 * creating-a-streaming-audio-app-for-android-with-android-media-mediaplayer-
 * android-media-audiomanager-and-android-app-service/
 * 
 * It is covered under  Creative Commons Attribution-ShareAlike 3.0 Unported License 
 * (http://creativecommons.org/licenses/by-sa/3.0/).
 */
/**
 * @author rootlicker http://speakingcode.com
 */
public interface IMediaPlayerServiceClient {
 
    /**
     * A callback made by a MediaPlayerService onto its clients to indicate that a player is initializing.
     * @param message A message to propagate to the client
     */
    public void onInitializePlayerStart(String message);
 
    /**
     * A callback made by a MediaPlayerService onto its clients to indicate that a player was successfully initialized.
     */
    public void onInitializePlayerSuccess();
 
    /**
     *  A callback made by a MediaPlayerService onto its clients to indicate that a player encountered an error.
     */
    public void onError();
    
    public void onServiceStopped();
    
    public void onPlayingSong(String title, String artist);
    
    public void onGotArtWork(Bitmap bitmap);
    
    public boolean isPaused();
}
