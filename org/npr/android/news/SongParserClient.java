package org.npr.android.news;

public interface SongParserClient {
	public void onGotMetaData(String metaString);

	public void onPlayingSong(String title, String artist);
	public void onSongParserError();
}
