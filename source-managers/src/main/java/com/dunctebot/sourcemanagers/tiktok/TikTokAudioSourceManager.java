package com.dunctebot.sourcemanagers.tiktok;

import com.dunctebot.sourcemanagers.AbstractDuncteBotHttpSource;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dunctebot.sourcemanagers.Utils.fakeChrome;

public class TikTokAudioSourceManager extends AbstractDuncteBotHttpSource {
    private final TikTokAudioTrackHttpManager httpManager = new TikTokAudioTrackHttpManager();
    private static final String BASE = "https://(?:www\\.|m\\.)?tiktok\\.com";
    private static final String USER = "@(?<user>[^/]+)";
    private static final String VIDEO = "(?<video>[0-9]+)";
    protected static final Pattern VIDEO_REGEX = Pattern.compile("^" + BASE + "/" + USER + "/video/" + VIDEO + "(?:.*)$");

    public TikTokAudioSourceManager() {
        super(false);
    }

    @Override
    public String getSourceName() {
        return "tiktok";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        final Matcher matcher = VIDEO_REGEX.matcher(reference.identifier);

        if (!matcher.matches()) {
            return null;
        }

        final String user = matcher.group("user");
        final String video = matcher.group("video");

        try {
            final MetaData metaData = extractData(user, video);
            return new TikTokAudioTrack(metaData.toTrackInfo(), this);
        } catch (Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Error al cargar el audio de TikTok", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {}

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new TikTokAudioTrack(trackInfo, this);
    }

    MetaData extractData(String userId, String videoId) throws Exception {
        return extractData("https://www.tiktok.com/@" + userId + "/video/" + videoId);
    }

    @Override
    public HttpInterface getHttpInterface() {
        return httpManager.getHttpInterface();
    }

    protected MetaData extractData(String url) throws Exception {
        final HttpGet httpGet = new HttpGet(url);
        fakeChrome(httpGet);

        try (final CloseableHttpResponse response = getHttpInterface().execute(httpGet)) {
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IOException("Código de estado inesperado: " + statusCode);
            }

            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            JsonBrowser json = JsonBrowser.parse(html);
            
            // Verifica si TikTok ha cambiado la estructura del JSON
            if (json.isNull() || json.get("ItemModule").isNull()) {
                throw new FriendlyException("No se encontraron datos del video de TikTok", FriendlyException.Severity.SUSPICIOUS, null);
            }

            final String videoId = json.get("ItemList").get("video").index(0).text();
            final JsonBrowser base = json.get("ItemModule").get(videoId);
            
            return getMetaData(url, base);
        }
    }

    protected static MetaData getMetaData(String url, JsonBrowser base) {
        final MetaData metaData = new MetaData();
        final JsonBrowser videoJson = base.get("video");

        metaData.pageUrl = url;
        metaData.videoId = base.get("id").safeText();
        metaData.videoUrl = videoJson.get("playAddr").safeText();
        metaData.cover = videoJson.get("cover").safeText();
        metaData.title = base.get("desc").safeText();
        metaData.duration = Integer.parseInt(videoJson.get("duration").safeText());
        metaData.musicUrl = base.get("music").get("playUrl").text();
        metaData.uniqueId = base.get("author").safeText();

        return metaData;
    }

    protected static class MetaData {
        String cover;
        String pageUrl;
        String videoId;
        String videoUrl;
        int duration;
        String title;
        String musicUrl;
        String uniqueId;

        AudioTrackInfo toTrackInfo() {
            return new AudioTrackInfo(
                this.title,
                this.uniqueId,
                this.duration * 1000L,
                this.videoId,
                false,
                this.pageUrl,
                this.cover,
                this.videoUrl
            );
        }
    }
}
