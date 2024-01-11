package com.amrsubzero.media3test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaNotification;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.MediaStyleNotificationHelper;

import com.google.common.collect.ImmutableList;

public class PlaybackService extends MediaSessionService {

    ExoPlayer player;

    MediaSession mediaSession;

    private NotificationCompat.Builder notificationBuilder;
    NotificationManager notificationManager;

    MediaNotification.ActionFactory notificationActionFactory;

    ProgressTracker tracker;

    @Override
    @UnstableApi
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA).build(), true)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();

        player.setRepeatMode(Player.REPEAT_MODE_OFF);

        player.addListener(new Player.Listener() {

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {

                if(isPlaying) {

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
        });

        mediaSession = new MediaSession.Builder(this, player).build();

        setMediaNotificationProvider(new MediaNotification.Provider() {
            @NonNull
            @Override
            public MediaNotification createNotification(@NonNull MediaSession mediaSession, @NonNull ImmutableList<CommandButton> customLayout, @NonNull MediaNotification.ActionFactory actionFactory, @NonNull Callback onNotificationChangedCallback) {

                notificationActionFactory = actionFactory;

                createMediaNotification(mediaSession);

                return new MediaNotification(getResources().getInteger(R.integer.PLAYBACK_NOTIFICATION_ID), notificationBuilder.build());
            }

            @Override
            public boolean handleCustomCommand(@NonNull MediaSession session, @NonNull String action, @NonNull Bundle extras) {
                return false;
            }
        });
    }

    @UnstableApi
    public void createMediaNotification(MediaSession mediaSession) {

        MediaStyleNotificationHelper.MediaStyle mediaStyle = new MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowCancelButton(false)
                .setShowActionsInCompactView(0);

        int action_play_pause_icon = (mediaSession.getPlayer().isPlaying()) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String action_play_pause_title = (mediaSession.getPlayer().isPlaying()) ? "Pause" : "Play";

        NotificationCompat.Action playPauseAction = notificationActionFactory.createMediaAction(mediaSession, IconCompat.createWithResource(PlaybackService.this, action_play_pause_icon), action_play_pause_title, Player.COMMAND_PLAY_PAUSE);

        NotificationCompat.Action stopAction = notificationActionFactory.createMediaAction(mediaSession, IconCompat.createWithResource(PlaybackService.this, R.drawable.ic_stop), "Stop", Player.COMMAND_STOP);

        PendingIntent stopPendingIntent = notificationActionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_STOP);

        boolean isPlaying = mediaSession.getPlayer().isPlaying();
        MediaMetadata metadata = mediaSession.getPlayer().getMediaMetadata();

        notificationBuilder = new NotificationCompat.Builder(PlaybackService.this, getResources().getString(R.string.PLAYBACK_CHANNEL_ID))
                .setPriority(Notification.PRIORITY_MAX)
                .setChannelId(getResources().getString(R.string.PLAYBACK_CHANNEL_ID))

                .addAction(playPauseAction)
                .addAction(stopAction)

                .setOngoing(true)
                .setWhen(isPlaying ? System.currentTimeMillis() - mediaSession.getPlayer().getCurrentPosition() : 0)
                .setShowWhen(isPlaying)
                .setUsesChronometer(isPlaying)

                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.artwork))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)

                .setContentTitle(metadata.title)
                .setContentText(metadata.description)

                .setColorized(true)
                .setColor(getResources().getColor(R.color.turquoise))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                //.setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession))
                .setStyle(mediaStyle)
                .setContentIntent(createContentIntent())
                .setDeleteIntent(stopPendingIntent);
    }

    private void createNotificationChannel() {

        String PLAYBACK_CHANNEL_ID = getResources().getString(R.string.PLAYBACK_CHANNEL_ID);
        String PLAYBACK_CHANNEL_NAME = getResources().getString(R.string.PLAYBACK_CHANNEL_NAME);
        String PLAYBACK_CHANNEL_DESCRIPTION = getResources().getString(R.string.PLAYBACK_CHANNEL_DESCRIPTION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW; // IMPORTANCE_LOW to disable notification sound
            NotificationChannel channel =
                    new NotificationChannel(PLAYBACK_CHANNEL_ID, PLAYBACK_CHANNEL_NAME, importance);
            channel.setDescription(PLAYBACK_CHANNEL_DESCRIPTION);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @UnstableApi
    private PendingIntent createContentIntent() {

        Intent intent = new Intent(PlaybackService.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(PlaybackService.this, getResources().getInteger(R.integer.PLAYBACK_NOTIFICATION_INTENT_CODE), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }else {
            pendingIntent = PendingIntent.getActivity(PlaybackService.this,getResources().getInteger(R.integer.PLAYBACK_NOTIFICATION_INTENT_CODE), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return pendingIntent;
    }

    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {

        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player player = mediaSession.getPlayer();
        if (!player.getPlayWhenReady() || player.getMediaItemCount() == 0) {
            // Stop the service if not playing, continue playing in the background
            // otherwise.
            stopSelf();
        }
    }

}