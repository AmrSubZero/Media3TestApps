package com.amrsubzero.media3test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.LegacyPlayerControlView;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    Button play_button, stop_button;

    TextView playback_output;

    CircularProgressIndicator playback_buffering;

    MediaController player;
    ListenableFuture<MediaController> controllerFuture;

    ProgressTracker tracker;

    LegacyPlayerControlView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.playerView);

        play_button = findViewById(R.id.play_button);

        play_button.setOnClickListener(view -> {

            if(player == null) { return; }

            if(player.getMediaItemCount() < 1) {
                initialize_player();
            }

            if(player.isPlaying()) {
                player.pause();
            }else {
                player.play();
            }
        });

        stop_button = findViewById(R.id.stop_button);

        stop_button.setOnClickListener(view -> {

            if(player == null) { return; }

            player.seekTo(player.getCurrentMediaItemIndex(), 0);
            player.stop();

            play_button.setText(getString(R.string.playback_play));
        });

        playback_buffering = findViewById(R.id.playback_buffering);

        playback_output = findViewById(R.id.playback_output);

    }

    public void initialize_player() {

        List<MediaItem> mediaItems = new ArrayList<>();

        String[] media_urls = {
                "https://github.com/rafaelreis-hotmart/Audio-Sample-files/raw/master/sample.mp3",
                "https://github.com/rafaelreis-hotmart/Audio-Sample-files/raw/master/db539f71-b296-4a4a-922d-f207f54154e0.m4a",
                "https://github.com/rafaelreis-hotmart/Audio-Sample-files/raw/master/a20cce3a-1d05-4f9a-9d31-f9d917049a24.m4a",
        };

        Uri artworkUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.artwork);

        for (int i = 0; i < media_urls.length; i++) {

            MediaItem mediaItem =
                new MediaItem.Builder()
                    .setMediaId(String.valueOf(i))
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setTitle("Track "+(i + 1))
                            .setDescription("Example Media3 App")
                            .setArtworkUri(artworkUri)
                            .build())
                    .setUri(media_urls[i])
                    .build();

            mediaItems.add(mediaItem);
        }

        player.setMediaItems(mediaItems);

    }

    @UnstableApi
    @Override
    protected void onStart() {

        super.onStart();

        SessionToken sessionToken =
                new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture =
                new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {

            try {
                player = controllerFuture.get();

                if(player.getMediaItemCount() < 1) {
                    initialize_player();
                }

                playerView.setPlayer(player);

                player.addListener(new Player.Listener() {

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {

                        if(playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                            stop_button.setVisibility(View.VISIBLE);
                        }else {
                            stop_button.setVisibility(View.GONE);
                        }

                        if(playbackState == Player.STATE_BUFFERING) {
                            playback_buffering.setVisibility(View.VISIBLE);
                            play_button.setEnabled(false);
                        }else {
                            playback_buffering.setVisibility(View.GONE);
                            play_button.setEnabled(true);
                        }

                        switch (playbackState) {
                            case Player.STATE_READY:
                                playback_output.setText(getString(R.string.playback_state_ready));
                                break;
                            case Player.STATE_BUFFERING:
                                playback_output.setText(getString(R.string.playback_state_buffering));
                                break;
                            case Player.STATE_ENDED:
                                playback_output.setText(getString(R.string.playback_state_ended));
                                break;
                            case Player.STATE_IDLE:
                                playback_output.setText(getString(R.string.playback_state_idle));
                                break;
                            default:
                                playback_output.setText(getString(R.string.playback_state_initializing));
                                break;
                        }

                        if(playbackState != Player.STATE_READY) {
                            if(tracker != null) { tracker.purgeHandler(); tracker = null; }
                        }

                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {

                        if(isPlaying) {

                            play_button.setText(getString(R.string.playback_pause));

                            tracker = new ProgressTracker(player, 1000, current_position -> {

                                int current_seconds = (int)(current_position / 1000);

                                Log.i("PLAYBACK_STATE", "position: " + current_position);

                                if(player != null) {

                                    MediaItem oldItem = player.getCurrentMediaItem();
                                    if(oldItem != null) {
                                        MediaItem newItem = oldItem
                                                .buildUpon()
                                                .setMediaMetadata(player.getMediaMetadata()
                                                        .buildUpon()
                                                        .setTitle("Track " + (player.getCurrentMediaItemIndex() + 1) + " (Part "+ current_seconds + ")")
                                                        .build())
                                                .build();
                                        player.replaceMediaItem(player.getCurrentMediaItemIndex(), newItem);
                                    }

                                    // Log.i("PLAYBACK_STATE", "\"replaceMediaItem()\" called.");

                                }else {
                                    Log.i("PLAYBACK_STATE", "Player is NULL");
                                }

                            });

                        }else {
                            play_button.setText(getString(R.string.playback_play));

                            if(tracker != null) { tracker.purgeHandler(); tracker = null; }
                        }

                    }

                    @Override
                    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                        /*
                        if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                            Log.i("PLAYBACK_STATE", "EVENT_MEDIA_METADATA_CHANGED");
                        }
                        */
                    }

                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {

                        play_button.setText(getString(R.string.playback_play));

                        play_button.setText(error.getMessage());
                    }

                });

            } catch (Exception e) {
                e.printStackTrace();

                Log.i("PLAYBACK_STATE", "Playback Exception: " + e.getMessage());
            }

        }, MoreExecutors.directExecutor());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(player != null) { player.release(); }

        if(tracker != null) { tracker.purgeHandler(); tracker = null; }
    }
}