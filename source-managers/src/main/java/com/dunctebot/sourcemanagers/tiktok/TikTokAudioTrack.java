package com.dunctebot.sourcemanagers.tiktok;

import com.dunctebot.sourcemanagers.AbstractDuncteBotHttpSource;
import com.dunctebot.sourcemanagers.MpegTrack;
import com.dunctebot.sourcemanagers.Pair;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class TikTokAudioTrack extends MpegTrack {
    private Pair<String, String> urlCache = null;
    private boolean failedOnce = false;

    public TikTokAudioTrack(AudioTrackInfo trackInfo, AbstractDuncteBotHttpSource manager) {
        super(trackInfo, manager);
    }

    public Pair<String, String> getUrlCache() {
        return urlCache;
    }

    @Override
    public String getPlaybackUrl() {
        try {
            if (this.urlCache == null) {
                this.urlCache = loadPlaybackUrl();
            }

            if (this.urlCache == null) {
                throw new FriendlyException("Failed to load TikTok playback URL.", SUSPICIOUS, null);
            }

            return this.failedOnce ? this.urlCache.getRight() : this.urlCache.getLeft();
        } catch (Exception e) {
            throw new FriendlyException("Could not load TikTok video: " + e.getMessage(), SUSPICIOUS, e);
        }
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = this.getHttpInterface()) {
            loadStream(executor, httpInterface);
        }
    }

    @Override
    protected void loadStream(LocalAudioTrackExecutor localExecutor, HttpInterface httpInterface) throws Exception {
        try {
            super.loadStream(localExecutor, httpInterface);
        } catch (Exception e) {
            if (this.failedOnce) {
                throw new FriendlyException("Failed to load TikTok audio after retrying.", SUSPICIOUS, e);
            }

            this.failedOnce = true;
            this.urlCache = null;  // Limpiar caché antes de intentar recargar
            this.urlCache = loadPlaybackUrl();

            if (this.urlCache == null) {
                throw new FriendlyException("Could not retrieve a valid TikTok playback URL.", SUSPICIOUS, e);
            }

            super.loadStream(localExecutor, httpInterface);
        }
    }

    protected Pair<String, String> loadPlaybackUrl() throws Exception {
        TikTokAudioSourceManager.MetaData metadata = this.getSourceManager().extractData(
            this.trackInfo.author,
            this.trackInfo.identifier
        );

        if (metadata == null || metadata.videoUrl == null || metadata.musicUrl == null) {
            throw new FriendlyException("Invalid TikTok metadata received.", SUSPICIOUS, null);
        }

        return new Pair<>(metadata.videoUrl, metadata.musicUrl);
    }

    @Override
    protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
        if (this.failedOnce && this.urlCache != null && this.urlCache.getRight() != null && this.urlCache.getRight().contains(".mp3")) {
            return new Mp3AudioTrack(trackInfo, stream);
        }
        return super.createAudioTrack(trackInfo, stream);
    }

    @Override
    protected long getTrackDuration() {
        return Units.CONTENT_LENGTH_UNKNOWN;
    }

    @Override
    protected HttpInterface getHttpInterface() {
        return this.getSourceManager().getHttpInterface();
    }

    @Override
    public TikTokAudioSourceManager getSourceManager() {
        return (TikTokAudioSourceManager) super.getSourceManager();
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new TikTokAudioTrack(this.trackInfo, getSourceManager());
    }
}
