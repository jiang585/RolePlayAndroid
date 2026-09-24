package com.example.roleplaychat.ui.chat;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.data.file.ImageFavoriteStore;
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.domain.model.MessageAttachment;
import com.bumptech.glide.Glide;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * 群聊主界面（S9）：类微信角色扮演群聊。
 * 顶栏复用系统 ActionBar（返回/成员/装扮/更多）、生成状态条（弹跳点+停止）、
 * 消息区（背景+覆盖层）、底部输入栏（动作/输入/发送/推进）。
 */
public class ChatFragment extends Fragment {

    private ChatViewModel viewModel;
    private ChatMessageAdapter adapter;
    private RecyclerView recyclerView;
    private MaterialToolbar toolbar;
    private EditText inputBox;
    private MaterialButton sendButton;
    private MaterialButton narrationButton;
    private MaterialButton stopGenerationButton;
    private View generationStatus;
    private View dot1;
    private View dot2;
    private View dot3;
    private TextView newMessagesTip;
    private LinearLayoutManager layoutManager;
    private boolean userScrolledUp;
    private String scriptId;
    private final List<ObjectAnimator> dotAnimators = new ArrayList<>();
    private int memberCount;
    private String identityName = "";
    private ImageFavoriteStore favoriteStore;
    private File pendingSaveFile;
    private final androidx.activity.result.ActivityResultLauncher<Intent> saveDocumentLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null
                        && result.getData().getData() != null && pendingSaveFile != null) {
                    copyToUri(pendingSaveFile, result.getData().getData());
                }
                pendingSaveFile = null;
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        scriptId = getArguments() == null ? null : getArguments().getString("scriptId");
        favoriteStore = new ImageFavoriteStore(requireContext());

        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(ChatViewModel.class);
        viewModel.setScriptId(scriptId);

        bindViews(view);
        setupToolbar();
        setupRecycler();
        setupListeners(view);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);

        ((RolePlayChatApp) requireActivity().getApplication()).container()
                .scriptRepository.observeById(scriptId)
                .observe(getViewLifecycleOwner(), this::renderTitle);
        ((RolePlayChatApp) requireActivity().getApplication()).container()
                .characterRepository.observeByScriptId(scriptId)
                .observe(getViewLifecycleOwner(), this::onMembersChanged);
    }

    @Override
    public void onDestroyView() {
        stopDotAnimation();
        cleanupToolbar();
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        adapter = null;
        recyclerView = null;
        layoutManager = null;
        inputBox = null;
        sendButton = null;
        narrationButton = null;
        stopGenerationButton = null;
        generationStatus = null;
        dot1 = null;
        dot2 = null;
        dot3 = null;
        newMessagesTip = null;
        toolbar = null;
        super.onDestroyView();
    }

    private void bindViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_messages);
        inputBox = view.findViewById(R.id.input_message);
        sendButton = view.findViewById(R.id.btn_send);
        narrationButton = view.findViewById(R.id.btn_narration);
        stopGenerationButton = view.findViewById(R.id.btn_stop_generation);
        generationStatus = view.findViewById(R.id.generation_status);
        dot1 = view.findViewById(R.id.dot_1);
        dot2 = view.findViewById(R.id.dot_2);
        dot3 = view.findViewById(R.id.dot_3);
        newMessagesTip = view.findViewById(R.id.tv_new_messages);
    }

    /** 复用 Activity 的 Toolbar 作为聊天顶栏（避免双栏与状态栏遮挡）。 */
    private void setupToolbar() {
        if (getActivity() == null) {
            return;
        }
        toolbar = getActivity().findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(getString(R.string.chat_back));
        toolbar.setNavigationOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
        toolbar.getMenu().clear();
        toolbar.inflateMenu(R.menu.menu_chat);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.s9_identity_subtitle));
        for (int i = 0; i < toolbar.getMenu().size(); i++) {
            toolbar.getMenu().getItem(i).setIconTintList(tint);
        }
    }

    private void cleanupToolbar() {
        if (toolbar != null) {
            toolbar.getMenu().clear();
            toolbar.setOnMenuItemClickListener(null);
            toolbar.setNavigationOnClickListener(null);
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_chat_members) {
            showMembersDialog();
            return true;
        } else if (id == R.id.menu_chat_appearance) {
            navigateToAppearance();
            return true;
        } else if (id == R.id.menu_chat_rules) {
            navigateToChatRules();
            return true;
        } else if (id == R.id.menu_chat_moments) {
            openMoments(null);
            return true;
        } else if (id == R.id.menu_chat_send_image) {
            showCharacterImageDialog();
            return true;
        } else if (id == R.id.menu_chat_favorites) {
            showFavorites();
            return true;
        } else if (id == R.id.menu_chat_clear) {
            confirmClearChat();
            return true;
        }
        return false;
    }

    private void showMembersDialog() {
        RolePlayChatApp app = (RolePlayChatApp) requireActivity().getApplication();
        app.container().executors.diskIO().execute(() -> {
            List<CharacterProfile> characters = app.container().characterRepository
                    .getEnabledByScriptId(scriptId);
            final String[] names = new String[characters.size()];
            for (int i = 0; i < characters.size(); i++) {
                names[i] = characters.get(i).getName();
            }
            app.container().executors.mainThread().execute(() -> {
                if (getView() == null) {
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.chat_member_dialog_title, names.length))
                        .setItems(names, null)
                        .setPositiveButton(R.string.action_close, null)
                        .show();
            });
        });
    }

    private void showCharacterImageDialog() {
        RolePlayChatApp app = (RolePlayChatApp) requireActivity().getApplication();
        app.container().executors.diskIO().execute(() -> {
            List<CharacterProfile> characters = app.container().characterRepository.getEnabledByScriptId(scriptId);
            app.container().executors.mainThread().execute(() -> {
                if (getView() == null || characters.isEmpty()) {
                    Toast.makeText(requireContext(), "没有可用角色", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[characters.size()];
                for (int i = 0; i < characters.size(); i++) names[i] = characters.get(i).getName();
                EditText description = new EditText(requireContext());
                description.setHint("描述照片内容（可留空）");
                description.setSingleLine(false);
                int padding = (int) (20 * getResources().getDisplayMetrics().density);
                description.setPadding(padding, padding / 2, padding, padding / 2);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("选择发送照片的角色")
                        .setSingleChoiceItems(names, 0, null)
                        .setView(description)
                        .setPositiveButton("发送", (dialog, which) -> {
                            androidx.appcompat.app.AlertDialog alert = (androidx.appcompat.app.AlertDialog) dialog;
                            int selected = alert.getListView().getCheckedItemPosition();
                            if (selected < 0) selected = 0;
                            viewModel.sendCharacterImage(characters.get(selected).getId(), description.getText().toString());
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            });
        });
    }

    private void navigateToAppearance() {
        androidx.navigation.NavController navController =
                androidx.navigation.Navigation.findNavController(requireView());
        Bundle args = new Bundle();
        args.putString("scriptId", scriptId);
        navController.navigate(R.id.action_chat_to_appearance, args);
    }

    private void navigateToChatRules() {
        androidx.navigation.NavController navController =
                androidx.navigation.Navigation.findNavController(requireView());
        Bundle args = new Bundle();
        args.putString("scriptId", scriptId);
        navController.navigate(R.id.action_chat_to_chatRuleEdit, args);
    }

    private void confirmClearChat() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chat_clear_history)
                .setMessage(R.string.chat_clear_confirm)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    viewModel.clearChat();
                    Toast.makeText(requireContext(), R.string.action_confirm, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void setupRecycler() {
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatMessageAdapter(this::openMoments, this::mentionCharacter, this::openImage);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                userScrolledUp = lastVisible < total - 3;
                if (newMessagesTip.getVisibility() == View.VISIBLE && !userScrolledUp) {
                    newMessagesTip.setVisibility(View.GONE);
                }
                if (layoutManager.findFirstVisibleItemPosition() <= 1 && total > 0) {
                    viewModel.loadEarlier();
                }
            }
        });
    }

    private void openImage(ChatMessage message, MessageAttachment attachment) {
        if (attachment == null) return;
        showImage(attachment.getLocalPath());
    }

    private void showImage(String localPath) {
        if (!isAdded() || localPath == null) return;
        File file = assetStore().resolve(localPath);
        if (file == null) {
            Toast.makeText(requireContext(), "图片文件已不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        ImageView image = new ImageView(requireContext());
        image.setBackgroundColor(android.graphics.Color.BLACK);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this).load(file).into(image);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(android.graphics.Color.BLACK);
        content.addView(image, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(android.view.Gravity.CENTER);
        actions.setPadding(12, 8, 12, 12);
        Button save = new Button(requireContext()); save.setText("保存");
        Button favorite = new Button(requireContext()); favorite.setText(favoriteStore.isFavorite(localPath) ? "取消收藏" : "收藏");
        Button close = new Button(requireContext()); close.setText("关闭");
        actions.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        actions.addView(favorite, new LinearLayout.LayoutParams(0, -2, 1f));
        actions.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(content);
        save.setOnClickListener(v -> saveImage(file));
        favorite.setOnClickListener(v -> {
            boolean added = favoriteStore.toggle(localPath);
            favorite.setText(added ? "取消收藏" : "收藏");
            Toast.makeText(requireContext(), added ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
        });
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showFavorites() {
        if (!isAdded()) return;
        java.util.List<String> paths = new ArrayList<>();
        for (String path : favoriteStore.all()) if (assetStore().resolve(path) != null) paths.add(path);
        Collections.sort(paths, Collections.reverseOrder());
        if (paths.isEmpty()) {
            Toast.makeText(requireContext(), "还没有收藏的图片，打开图片后长按即可收藏", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[paths.size()];
        for (int i = 0; i < paths.size(); i++) labels[i] = new File(paths.get(i)).getName();
        new MaterialAlertDialogBuilder(requireContext()).setTitle("图片收藏")
                .setItems(labels, (d, which) -> showImage(paths.get(which)))
                .setNegativeButton(R.string.action_close, null).show();
    }

    private LocalAssetStore assetStore() {
        return ((RolePlayChatApp) requireActivity().getApplication()).container().assetStore;
    }

    private void saveImage(File source) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingSaveFile = source;
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/png")
                    .putExtra(Intent.EXTRA_TITLE, "RolePlayChat_" + System.currentTimeMillis() + ".png");
            saveDocumentLauncher.launch(create);
            return;
        }
        RolePlayChatApp app = (RolePlayChatApp) requireActivity().getApplication();
        app.container().executors.diskIO().execute(() -> {
            Uri destination = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "RolePlayChat_" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RolePlayChat");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                destination = app.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (destination == null) throw new java.io.IOException("无法创建相册文件");
                try (FileInputStream input = new FileInputStream(source);
                     OutputStream output = app.getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new java.io.IOException("无法写入相册");
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                ContentValues done = new ContentValues(); done.put(MediaStore.Images.Media.IS_PENDING, 0);
                app.getContentResolver().update(destination, done, null, null);
                showSaveResult("已保存到相册");
            } catch (Exception error) {
                if (destination != null) app.getContentResolver().delete(destination, null, null);
                showSaveResult("保存失败：" + error.getMessage());
            }
        });
    }

    private void copyToUri(File source, Uri destination) {
        RolePlayChatApp app = (RolePlayChatApp) requireActivity().getApplication();
        app.container().executors.diskIO().execute(() -> {
            try (FileInputStream input = new FileInputStream(source);
                 OutputStream output = app.getContentResolver().openOutputStream(destination)) {
                if (output == null) throw new java.io.IOException("无法写入图片");
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                showSaveResult("图片已保存");
            } catch (Exception error) { showSaveResult("保存失败：" + error.getMessage()); }
        });
    }

    private void showSaveResult(String message) {
        if (getActivity() != null) requireActivity().runOnUiThread(() -> {
            if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupListeners(View view) {
        sendButton.setOnClickListener(v -> {
            String text = inputBox.getText() == null ? "" : inputBox.getText().toString();
            if (!text.trim().isEmpty()) {
                viewModel.sendMessage(text);
                inputBox.setText("");
                updateSendEnabled();
            }
        });
        narrationButton.setOnClickListener(v -> showNarrationDialog());
        stopGenerationButton.setOnClickListener(v -> viewModel.stopGeneration());
        newMessagesTip.setOnClickListener(v -> scrollToBottom());
        inputBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendEnabled();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        updateSendEnabled();
    }

    private void mentionCharacter(String displayName) {
        String current = inputBox.getText() == null ? "" : inputBox.getText().toString();
        String mention = "@" + displayName + " ";
        if (!current.contains(mention)) {
            inputBox.setText(current + (current.trim().isEmpty() ? "" : " ") + mention);
        }
        inputBox.requestFocus();
        inputBox.setSelection(inputBox.length());
        updateSendEnabled();
    }

    private void openMoments(@Nullable ChatMessage message) {
        if (scriptId == null) return;
        Bundle args = new Bundle();
        args.putString("scriptId", scriptId);
        args.putString("profileCharacterId", message == null ? null : message.getCharacterId());
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.action_chat_to_moments, args);
    }

    private void updateSendEnabled() {
        String text = inputBox.getText() == null ? "" : inputBox.getText().toString();
        sendButton.setEnabled(!text.trim().isEmpty());
        sendButton.setAlpha(text.trim().isEmpty() ? 0.4f : 1f);
    }

    private void showNarrationDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.chat_narration_hint);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chat_send_narration)
                .setView(input)
                .setPositiveButton(R.string.chat_send, (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString();
                    viewModel.sendNarration(text);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void render(ChatUiState state) {
        if (state == null) {
            return;
        }
        adapter.setAppearance(state.getAppearance());
        // DiffUtil 异步更新列表；必须在提交完成且 RecyclerView 完成布局后再滚动。
        final boolean followLatest = !userScrolledUp;
        adapter.submitList(state.getItems(), () -> {
            if (getView() == null || recyclerView == null) {
                return;
            }
            recyclerView.post(() -> {
                if (getView() == null || recyclerView == null) {
                    return;
                }
                if (followLatest) {
                    scrollToBottom();
                    newMessagesTip.setVisibility(View.GONE);
                } else if (adapter.getItemCount() > 0) {
                    newMessagesTip.setVisibility(View.VISIBLE);
                }
            });
        });
        if (state.getAppearance() != null) {
            applyBackground(state.getAppearance());
        }
        // 生成状态条
        if (state.isGenerating()) {
            generationStatus.setVisibility(View.VISIBLE);
            startDotAnimation();
        } else {
            generationStatus.setVisibility(View.GONE);
            stopDotAnimation();
            if (userScrolledUp) {
                newMessagesTip.setVisibility(View.VISIBLE);
            }
        }
        // 顶栏副标题：身份
        if (state.getIdentity() != null) {
            updateIdentitySubtitle(state.getIdentity());
        }
    }

    private void updateIdentitySubtitle(PlayerIdentity identity) {
        if (!isAdded() || getView() == null) {
            return;
        }
        if (identity.isObserver()) {
            identityName = getString(R.string.chat_identity_observer_label);
            refreshSubtitle();
            return;
        }
        if (identity.getCharacterId() != null) {
            RolePlayChatApp app = (RolePlayChatApp) requireActivity().getApplication();
            app.container().executors.diskIO().execute(() -> {
                CharacterProfile profile = app.container().characterRepository
                        .getById(identity.getCharacterId());
                final String name = profile == null
                        ? getString(R.string.common_unknown) : profile.getName();
                app.container().executors.mainThread().execute(() -> {
                    if (!isAdded() || getView() == null || toolbar == null) {
                        return;
                    }
                    identityName = getString(R.string.chat_identity_current, name);
                    refreshSubtitle();
                });
            });
        }
    }

    private void refreshSubtitle() {
        if (toolbar == null) {
            return;
        }
        String subtitle = memberCount > 0
                ? getString(R.string.chat_member_count, memberCount) + " · " + identityName
                : identityName;
        toolbar.setSubtitle(subtitle);
    }

    private void onMembersChanged(List<CharacterProfile> characters) {
        memberCount = characters == null ? 0 : characters.size();
        refreshSubtitle();
    }

    private void applyBackground(Appearance appearance) {
        View container = getView() == null ? null : getView().findViewById(R.id.message_container);
        if (container == null) {
            return;
        }
        if (appearance.getBackgroundType() == Appearance.BackgroundType.IMAGE
                && appearance.getBackgroundRef() != null) {
            java.io.File file = ((RolePlayChatApp) requireActivity().getApplication())
                    .container().assetStore.resolve(appearance.getBackgroundRef());
            if (file != null) {
                container.setBackground(android.graphics.drawable
                        .BitmapDrawable.createFromPath(file.getAbsolutePath()));
                return;
            }
        }
        container.setBackgroundColor(requireContext().getColor(R.color.s9_page_background));
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.startsWith("error:")) {
            String code = value.substring("error:".length());
            Toast.makeText(requireContext(), mapError(code), Toast.LENGTH_SHORT).show();
        }
    }

    private String mapError(String code) {
        com.example.roleplaychat.domain.model.AppErrorCode errorCode =
                com.example.roleplaychat.domain.model.AppErrorCode.fromCode(code);
        return getString(com.example.roleplaychat.ui.common.ErrorMessageMapper.map(errorCode));
    }

    private void renderTitle(Script script) {
        if (script != null && toolbar != null) {
            toolbar.setTitle(script.getName());
        }
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    // ---------- 弹跳点动画 ----------

    private void startDotAnimation() {
        if (!dotAnimators.isEmpty()) {
            return;
        }
        TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();
        View[] dots = {dot1, dot2, dot3};
        long[] delays = {0L, 150L, 300L};
        for (int i = 0; i < dots.length; i++) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(dots[i], "translationY", 0f, -8f, 0f);
            animator.setDuration(600L);
            animator.setStartDelay(delays[i]);
            animator.setInterpolator(interpolator);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.start();
            dotAnimators.add(animator);
        }
    }

    private void stopDotAnimation() {
        for (ObjectAnimator animator : dotAnimators) {
            animator.cancel();
        }
        dotAnimators.clear();
    }
}
