package com.reelixy.videoplayer.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.utils.PreferenceUtils;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Grouped settings screen (Playback / Player / Appearance / Library / About),
 * built programmatically from the same row layout so every section stays
 * visually consistent without needing five near-identical XML files.
 */
public class SettingsActivity extends AppCompatActivity {

    private LinearLayout container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        container = findViewById(R.id.settingsContainer);

        buildPlaybackSection();
        buildPlayerSection();
        buildSubtitleSection();
        buildAppearanceSection();
        buildLibrarySection();
        buildAboutSection();
    }

    private void addHeader(String text) {
        TextView header = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_settings_header, container, false);
        header.setText(text);
        container.addView(header);
    }

    private View addSwitchRow(String label, String prefKey, boolean defaultValue) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_row, container, false);
        TextView tvLabel = row.findViewById(R.id.tvLabel);
        SwitchMaterial toggle = row.findViewById(R.id.switchToggle);
        tvLabel.setText(label);
        toggle.setVisibility(View.VISIBLE);
        toggle.setChecked(PreferenceUtils.getBoolean(this, prefKey, defaultValue));
        toggle.setOnCheckedChangeListener((btn, checked) ->
                PreferenceUtils.setBoolean(this, prefKey, checked));
        row.setOnClickListener(v -> toggle.toggle());
        container.addView(row);
        return row;
    }

    private View addValueRow(String label, String currentValue, Runnable onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_row, container, false);
        TextView tvLabel = row.findViewById(R.id.tvLabel);
        TextView tvValue = row.findViewById(R.id.tvValue);
        tvLabel.setText(label);
        tvValue.setText(currentValue);
        row.setOnClickListener(v -> onClick.run());
        container.addView(row);
        return row;
    }

    private void buildPlaybackSection() {
        addHeader(getString(R.string.section_playback));
        addSwitchRow(getString(R.string.resume_playback), PreferenceUtils.KEY_RESUME_PLAYBACK, true);
        addSwitchRow(getString(R.string.auto_play_next), PreferenceUtils.KEY_AUTO_PLAY_NEXT, true);
        addSwitchRow(getString(R.string.auto_pip), PreferenceUtils.KEY_AUTO_PIP, true);
        addSwitchRow(getString(R.string.default_subtitles), PreferenceUtils.KEY_DEFAULT_SUBTITLES, true);

        float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        float currentSpeed = PreferenceUtils.getFloat(this, PreferenceUtils.KEY_DEFAULT_SPEED, 1.0f);
        View speedRow = addValueRow(getString(R.string.default_playback_speed), currentSpeed + "x", null);
        speedRow.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, speedRow);
            for (int i = 0; i < speeds.length; i++) menu.getMenu().add(0, i, i, speeds[i] + "x");
            menu.setOnMenuItemClickListener(item -> {
                PreferenceUtils.setFloat(this, PreferenceUtils.KEY_DEFAULT_SPEED, speeds[item.getItemId()]);
                recreate();
                return true;
            });
            menu.show();
        });

        int[] skipOptions = {5000, 10000, 15000, 30000};
        int currentSkip = PreferenceUtils.getInt(this, PreferenceUtils.KEY_SKIP_DURATION_MS, 10000);
        View skipRow = addValueRow(getString(R.string.skip_duration), (currentSkip / 1000) + "s", null);
        skipRow.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, skipRow);
            for (int i = 0; i < skipOptions.length; i++) menu.getMenu().add(0, i, i, (skipOptions[i] / 1000) + "s");
            menu.setOnMenuItemClickListener(item -> {
                PreferenceUtils.setInt(this, PreferenceUtils.KEY_SKIP_DURATION_MS, skipOptions[item.getItemId()]);
                recreate();
                return true;
            });
            menu.show();
        });
    }

    private void buildPlayerSection() {
        addHeader(getString(R.string.section_player));
        addSwitchRow(getString(R.string.keep_screen_awake), PreferenceUtils.KEY_KEEP_SCREEN_AWAKE, true);

        int currentTimeout = PreferenceUtils.getInt(this, PreferenceUtils.KEY_AUTO_HIDE_CONTROLS_MS, 3000);
        addSwitchRow(getString(R.string.pause_dim), PreferenceUtils.KEY_PAUSE_DIM, true);
        float currentBoost = PreferenceUtils.getFloat(this, PreferenceUtils.KEY_DEFAULT_AUDIO_BOOST, 1.0f);
        View boostRow = addValueRow(getString(R.string.default_audio_boost), String.format(java.util.Locale.US, "%.0f%%", currentBoost * 100f), null);
        float[] boostOptions = {1.0f, 1.1f, 1.25f, 1.5f};
        boostRow.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, boostRow);
            for (int i = 0; i < boostOptions.length; i++) menu.getMenu().add(0, i, i, String.format(java.util.Locale.US, "%.0f%%", boostOptions[i] * 100f));
            menu.setOnMenuItemClickListener(item -> {
                PreferenceUtils.setFloat(this, PreferenceUtils.KEY_DEFAULT_AUDIO_BOOST, boostOptions[item.getItemId()]);
                recreate();
                return true;
            });
            menu.show();
        });

        float currentZoom = PreferenceUtils.getFloat(this, PreferenceUtils.KEY_DEFAULT_ZOOM, 1.0f);
        View zoomRow = addValueRow(getString(R.string.default_zoom), String.format(java.util.Locale.US, "%.2fx", currentZoom), null);
        float[] zoomOptions = {1.0f, 1.25f, 1.5f, 2.0f, 2.5f};
        zoomRow.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, zoomRow);
            for (int i = 0; i < zoomOptions.length; i++) menu.getMenu().add(0, i, i, String.format(java.util.Locale.US, "%.2fx", zoomOptions[i]));
            menu.setOnMenuItemClickListener(item -> {
                PreferenceUtils.setFloat(this, PreferenceUtils.KEY_DEFAULT_ZOOM, zoomOptions[item.getItemId()]);
                recreate();
                return true;
            });
            menu.show();
        });

        View row = addValueRow(getString(R.string.auto_hide_controls), (currentTimeout / 1000) + "s", null);
        int[] options = {2000, 3000, 5000, 8000};
        row.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, row);
            for (int i = 0; i < options.length; i++) menu.getMenu().add(0, i, i, (options[i] / 1000) + "s");
            menu.setOnMenuItemClickListener(item -> {
                PreferenceUtils.setInt(this, PreferenceUtils.KEY_AUTO_HIDE_CONTROLS_MS, options[item.getItemId()]);
                recreate();
                return true;
            });
            menu.show();
        });
    }

    private void buildSubtitleSection() {
        addHeader(getString(R.string.section_subtitle));
        int currentSize = PreferenceUtils.getInt(this, PreferenceUtils.KEY_SUBTITLE_FONT_SIZE, 100);
        View sizeRow = addValueRow(getString(R.string.subtitle_font_size), currentSize + "%", null);
        int[] sizes = {80, 90, 100, 115, 130, 150};
        sizeRow.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, sizeRow);
            for (int i = 0; i < sizes.length; i++) menu.getMenu().add(0, i, i, sizes[i] + "%");
            menu.setOnMenuItemClickListener(item -> {
                PreferenceUtils.setInt(this, PreferenceUtils.KEY_SUBTITLE_FONT_SIZE, sizes[item.getItemId()]);
                recreate();
                return true;
            });
            menu.show();
        });
    }

    private void buildAppearanceSection() {
        addHeader(getString(R.string.section_appearance));
        String[] modeLabels = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
        int currentMode = Math.max(0, Math.min(2, PreferenceUtils.getInt(this, PreferenceUtils.KEY_THEME_MODE, 0)));
        View row = addValueRow(getString(R.string.theme), modeLabels[currentMode], null);
        row.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, row);
            for (int i = 0; i < modeLabels.length; i++) menu.getMenu().add(0, i, i, modeLabels[i]);
            menu.setOnMenuItemClickListener(item -> {
                int mode = item.getItemId();
                PreferenceUtils.setInt(this, PreferenceUtils.KEY_THEME_MODE, mode);
                applyThemeMode(mode);
                recreate();
                return true;
            });
            menu.show();
        });
    }

    private void applyThemeMode(int mode) {
        switch (mode) {
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private void buildLibrarySection() {
        addHeader(getString(R.string.section_library));
        addValueRow(getString(R.string.rescan_media), "", null).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
    }

    private void buildAboutSection() {
        addHeader(getString(R.string.section_about));
        String versionName = "1.0";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        addValueRow(getString(R.string.version), versionName, null);
    }
}
