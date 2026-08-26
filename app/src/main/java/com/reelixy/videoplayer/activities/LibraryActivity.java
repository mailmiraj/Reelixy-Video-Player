package com.reelixy.videoplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.database.AppDatabase;
import com.reelixy.videoplayer.managers.MediaScanner;
import com.reelixy.videoplayer.managers.PermissionManager;
import com.reelixy.videoplayer.models.Folder;
import com.reelixy.videoplayer.models.Video;
import com.reelixy.videoplayer.utils.MediaFileManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated library screen with a clean, vertically stacked folder list.
 */
public class LibraryActivity extends AppCompatActivity {

    private static final int REQUEST_FOLDER_AUTHORIZATION = 7401;

    private PermissionManager permissionManager;
    private MediaScanner mediaScanner;

    private ProgressBar progressBar;
    private TextView tvSummary;
    private TextView tvStatVideos;
    private TextView tvStatFolders;
    private TextView tvStatState;
    private TextView btnLibraryScan;

    private View emptyState;

    private boolean destroyed;
    private boolean scanInProgress;

    /*
     * IMPORTANT:
     * This must be FolderOperation, not Runnable.
     */
    private FolderOperation pendingAuthorizedOperation;

    private List<Folder> currentFolders = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        permissionManager = new PermissionManager(this);
        mediaScanner = new MediaScanner(this);

        progressBar = findViewById(R.id.libraryProgress);
        tvSummary = findViewById(R.id.tvLibrarySummaryDetail);
        tvStatVideos = findViewById(R.id.tvLibraryStatVideos);
        tvStatFolders = findViewById(R.id.tvLibraryStatFolders);
        tvStatState = findViewById(R.id.tvLibraryStatState);
        emptyState = findViewById(R.id.libraryEmptyState);
        btnLibraryScan = findViewById(R.id.btnLibraryScan);

        findViewById(R.id.btnLibraryBack).setOnClickListener(v -> finish());

        btnLibraryScan.setOnClickListener(v -> {
            if (scanInProgress) {
                mediaScanner.cancelScan();
            } else {
                scanLibrary();
            }
        });

        findViewById(R.id.btnLibrarySort)
                .setOnClickListener(this::showSortMenu);

        findViewById(R.id.btnLibraryAll)
                .setOnClickListener(v ->
                        openLibraryFilter(SearchActivity.FILTER_ALL));

        findViewById(R.id.btnLibraryFavorites)
                .setOnClickListener(v ->
                        openLibraryFilter(SearchActivity.FILTER_FAVORITES));

        findViewById(R.id.btnLibraryRecent)
                .setOnClickListener(v ->
                        openLibraryFilter(SearchActivity.FILTER_RECENT));

        findViewById(R.id.btnLibraryPlaylists)
                .setOnClickListener(v ->
                        startActivity(new Intent(
                                this,
                                PlaylistDetailActivity.class
                        )));

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!destroyed
                && permissionManager != null
                && permissionManager.hasPermission(this)
                && !scanInProgress) {

            scanLibrary();
        }
    }

    private void load() {
        if (!permissionManager.hasPermission(this)) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        scanLibrary();
    }

    private void scanLibrary() {
        if (scanInProgress) {
            return;
        }

        scanInProgress = true;

        if (tvStatState != null) {
            tvStatState.setText(R.string.library_status_scanning);
        }

        if (btnLibraryScan != null) {
            btnLibraryScan.setText(R.string.cancel);
        }

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (emptyState != null) {
            emptyState.setVisibility(View.GONE);
        }

        mediaScanner.scanAsync(new MediaScanner.ScanCallback() {

            @Override
            public void onScanComplete(List<Video> videos) {
                if (destroyed) {
                    return;
                }

                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                scanInProgress = false;

                if (tvStatState != null) {
                    tvStatState.setText(R.string.library_status_ready);
                }

                if (btnLibraryScan != null) {
                    btnLibraryScan.setText(R.string.scan_device);
                }

                Map<String, Integer> counts = new LinkedHashMap<>();
                Map<String, String> names = new LinkedHashMap<>();

                for (Video video : videos) {
                    String key = video.getRelativePath();

                    if (key == null || key.isEmpty()) {
                        key = video.getFilePath();
                    }

                    counts.merge(key, 1, Integer::sum);
                    names.put(key, video.getFolderName());
                }

                List<Folder> folders = new ArrayList<>();

                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    folders.add(
                            new Folder(
                                    names.get(entry.getKey()),
                                    entry.getKey(),
                                    entry.getValue()
                            )
                    );
                }

                currentFolders = folders;

                if (tvStatVideos != null) {
                    tvStatVideos.setText(String.valueOf(videos.size()));
                }

                if (tvStatFolders != null) {
                    tvStatFolders.setText(String.valueOf(folders.size()));
                }

                if (tvStatState != null) {
                    tvStatState.setText(R.string.library_status_ready);
                }

                applyFolderSort(0);

                if (tvSummary != null) {
                    tvSummary.setText(
                            getString(
                                    R.string.library_summary_detail,
                                    videos.size(),
                                    folders.size()
                            )
                    );
                }

                if (emptyState != null) {
                    emptyState.setVisibility(
                            folders.isEmpty()
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                }
            }

            @Override
            public void onScanFailed(Exception e) {
                if (destroyed) {
                    return;
                }

                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                boolean cancelled =
                        e instanceof java.util.concurrent.CancellationException;

                scanInProgress = false;

                if (tvStatState != null) {
                    tvStatState.setText(
                            cancelled
                                    ? R.string.library_status_ready
                                    : R.string.library_status_issue
                    );
                }

                if (btnLibraryScan != null) {
                    btnLibraryScan.setText(R.string.scan_device);
                }

                if (!cancelled && emptyState != null) {
                    emptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onScanProgress(int scanned, int total) {
                if (destroyed) {
                    return;
                }

                if (tvSummary != null) {
                    tvSummary.setText(
                            getString(
                                    R.string.library_scan_progress,
                                    scanned,
                                    total
                            )
                    );
                }

                if (tvStatState != null) {
                    tvStatState.setText(
                            R.string.library_status_scanning
                    );
                }
            }
        });
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);

        menu.getMenu().add(
                0,
                1,
                0,
                getString(R.string.sort_name)
        );

        menu.getMenu().add(
                0,
                2,
                1,
                getString(R.string.sort_count)
        );

        menu.getMenu().add(
                0,
                3,
                2,
                getString(R.string.sort_count_desc)
        );

        menu.setOnMenuItemClickListener(item -> {
            applyFolderSort(item.getItemId());
            return true;
        });

        menu.show();
    }

    private void applyFolderSort(int mode) {
        List<Folder> sorted = new ArrayList<>(currentFolders);

        java.util.Collections.sort(sorted, (a, b) -> {

            if (mode == 2 || mode == 3) {
                int comparison = Integer.compare(
                        a.getVideoCount(),
                        b.getVideoCount()
                );

                return mode == 3
                        ? -comparison
                        : comparison;
            }

            return a.getName()
                    .compareToIgnoreCase(b.getName());
        });

        renderFolderList(sorted);
    }

    private void renderFolderList(List<Folder> folders) {
        android.widget.LinearLayout container =
                findViewById(R.id.folderListContainer);

        if (container == null) {
            return;
        }

        container.removeAllViews();

        LayoutInflater inflater =
                LayoutInflater.from(this);

        for (Folder folder : folders) {

            View row = inflater.inflate(
                    R.layout.item_folder,
                    container,
                    false
            );

            TextView tvName =
                    row.findViewById(R.id.tvFolderName);

            TextView tvCount =
                    row.findViewById(R.id.tvFolderCount);

            TextView tvBadge =
                    row.findViewById(R.id.tvFolderBadge);

            View more =
                    row.findViewById(R.id.btnFolderMore);

            tvName.setText(folder.getName());

            tvCount.setText(
                    getString(
                            R.string.video_count,
                            folder.getVideoCount()
                    )
            );

            tvBadge.setText(
                    String.valueOf(
                            folder.getVideoCount()
                    )
            );

            row.setContentDescription(
                    getString(
                            R.string.cd_folder_item,
                            folder.getName(),
                            folder.getVideoCount()
                    )
            );

            /*
             * Normal tap = open folder.
             */
            row.setClickable(true);
            row.setFocusable(true);

            row.setOnClickListener(
                    v -> openFolder(folder)
            );

            /*
             * Long press = folder management menu.
             */
            row.setOnLongClickListener(v -> {
                row.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS
                );

                showFolderActions(folder, row);
                return true;
            });

            /*
             * More button = folder management menu.
             */
            if (more != null) {
                more.setOnClickListener(
                        v -> showFolderActions(folder, more)
                );
            }

            container.addView(row);
        }

        container.setVisibility(
                folders.isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );
    }

    private void openFolder(Folder folder) {
        Intent intent =
                new Intent(this, SearchActivity.class);

        intent.putExtra(
                SearchActivity.EXTRA_FOLDER,
                folder.getName()
        );

        intent.putExtra(
                SearchActivity.EXTRA_FOLDER_PATH,
                folder.getPath()
        );

        startActivity(intent);
    }

    private void showFolderActions(
            Folder folder,
            View anchor
    ) {

        PopupMenu menu =
                new PopupMenu(this, anchor);

        menu.getMenu().add(
                0,
                1,
                0,
                getString(R.string.action_open_folder)
        );

        menu.getMenu().add(
                0,
                2,
                1,
                getString(R.string.action_rename_folder)
        );

        menu.getMenu().add(
                0,
                3,
                2,
                getString(R.string.action_delete_folder)
        );

        menu.setOnMenuItemClickListener(item -> {

            if (item.getItemId() == 1) {
                openFolder(folder);
                return true;
            }

            if (item.getItemId() == 2) {
                showRenameFolderDialog(folder);
                return true;
            }

            if (item.getItemId() == 3) {
                confirmDeleteFolder(folder);
                return true;
            }

            return false;
        });

        menu.show();
    }

    private void showRenameFolderDialog(Folder folder) {

        EditText input =
                new EditText(this);

        input.setSingleLine(true);
        input.setText(folder.getName());
        input.setSelectAllOnFocus(true);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.action_rename_folder)
                .setView(input)
                .setNegativeButton(
                        android.R.string.cancel,
                        null
                )
                .setPositiveButton(
                        R.string.save,
                        (dialog, which) -> {

                            String requested =
                                    input.getText()
                                            .toString()
                                            .trim();

                            if (requested.isEmpty()) {
                                return;
                            }

                            for (Folder existing : currentFolders) {

                                if (!existing.getPath()
                                        .equals(folder.getPath())
                                        && existing.getName()
                                        .equalsIgnoreCase(requested)) {

                                    Toast.makeText(
                                            this,
                                            R.string.folder_name_already_exists,
                                            Toast.LENGTH_LONG
                                    ).show();

                                    return;
                                }
                            }

                            runFolderOperation(() -> {

                                String oldPrefix =
                                        MediaFileManager.folderIdentity(
                                                folder.getPath()
                                        );

                                String normalized =
                                        requested.trim()
                                                .replace('\\', '/');

                                while (normalized.startsWith("/")) {
                                    normalized =
                                            normalized.substring(1);
                                }

                                while (normalized.endsWith("/")
                                        && !normalized.isEmpty()) {

                                    normalized =
                                            normalized.substring(
                                                    0,
                                                    normalized.length() - 1
                                            );
                                }

                                String oldPath =
                                        folder.getPath();

                                MediaFileManager.renameFolder(
                                        this,
                                        folder,
                                        requested
                                );

                                String newFolderPath;

                                if (android.os.Build.VERSION.SDK_INT
                                        >= android.os.Build.VERSION_CODES.Q) {

                                    String raw =
                                            oldPath == null
                                                    ? ""
                                                    : oldPath.trim()
                                                    .replace('\\', '/');

                                    while (raw.startsWith("/")) {
                                        raw = raw.substring(1);
                                    }

                                    if (!raw.endsWith("/")) {
                                        raw += "/";
                                    }

                                    int slash =
                                            raw.lastIndexOf(
                                                    '/',
                                                    raw.length() - 2
                                            );

                                    String parent =
                                            slash < 0
                                                    ? ""
                                                    : raw.substring(
                                                    0,
                                                    slash + 1
                                            );

                                    newFolderPath =
                                            parent
                                                    + normalized
                                                    + "/";

                                } else {

                                    java.io.File source =
                                            new java.io.File(oldPath);

                                    java.io.File target =
                                            new java.io.File(
                                                    source.getParentFile(),
                                                    normalized
                                            );

                                    newFolderPath =
                                            target.getPath();
                                }

                                AppDatabase.getInstance(this)
                                        .videoDao()
                                        .migrateFolderReferences(
                                                oldPrefix,
                                                MediaFileManager
                                                        .folderIdentity(
                                                                newFolderPath
                                                        )
                                        );
                            });
                        }
                )
                .show();
    }

    private void confirmDeleteFolder(Folder folder) {

        new android.app.AlertDialog.Builder(this)
                .setTitle(
                        R.string.action_delete_folder
                )
                .setMessage(
                        getString(
                                R.string.delete_folder_confirmation,
                                folder.getName(),
                                folder.getVideoCount()
                        )
                )
                .setNegativeButton(
                        android.R.string.cancel,
                        null
                )
                .setPositiveButton(
                        R.string.delete,
                        (dialog, which) ->
                                runFolderOperation(() -> {

                                    MediaFileManager.deleteFolder(
                                            this,
                                            folder
                                    );

                                    AppDatabase.getInstance(this)
                                            .videoDao()
                                            .removeFolderReferences(
                                                    MediaFileManager
                                                            .folderIdentity(
                                                                    folder.getPath()
                                                            )
                                            );
                                })
                )
                .show();
    }

    /*
     * Custom operation interface because folder operations
     * are allowed to throw checked exceptions.
     */
    private interface FolderOperation {
        void run() throws Exception;
    }

    private void runFolderOperation(
            FolderOperation operation
    ) {

        new Thread(() -> {

            try {

                operation.run();

                runOnUiThread(() -> {

                    Toast.makeText(
                            this,
                            R.string.operation_completed,
                            Toast.LENGTH_SHORT
                    ).show();

                    scanLibrary();
                });

            } catch (android.app.RecoverableSecurityException e) {

                /*
                 * Save exactly the same FolderOperation.
                 */
                pendingAuthorizedOperation = operation;

                runOnUiThread(
                        () -> requestUserAuthorization(e)
                );

            } catch (Exception e) {

                runOnUiThread(() -> {

                    String message =
                            e.getMessage() == null
                                    ? getString(
                                    R.string.operation_failed
                            )
                                    : e.getMessage();

                    Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }

        }, "folder-operation").start();
    }

    private void requestUserAuthorization(
            android.app.RecoverableSecurityException e
    ) {

        try {

            startIntentSenderForResult(
                    e.getUserAction()
                            .getActionIntent()
                            .getIntentSender(),
                    REQUEST_FOLDER_AUTHORIZATION,
                    null,
                    0,
                    0,
                    0
            );

        } catch (Exception ex) {

            pendingAuthorizedOperation = null;

            Toast.makeText(
                    this,
                    R.string.operation_failed,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_FOLDER_AUTHORIZATION) {

            if (resultCode == RESULT_OK
                    && pendingAuthorizedOperation != null) {

                /*
                 * IMPORTANT:
                 * FolderOperation, NOT Runnable.
                 */
                FolderOperation operation =
                        pendingAuthorizedOperation;

                pendingAuthorizedOperation = null;

                runFolderOperation(operation);

            } else if (resultCode != RESULT_OK) {

                pendingAuthorizedOperation = null;
            }
        }
    }

    private void openLibraryFilter(String filter) {

        Intent intent =
                new Intent(this, SearchActivity.class);

        intent.putExtra(
                SearchActivity.EXTRA_FILTER,
                filter
        );

        startActivity(intent);
    }

    @Override
    protected void onDestroy() {

        destroyed = true;

        if (mediaScanner != null) {
            mediaScanner.shutdown();
        }

        pendingAuthorizedOperation = null;

        super.onDestroy();
    }
}