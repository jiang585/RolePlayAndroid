package com.example.roleplaychat.ui.chat;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
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
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.usecase.ExportDataUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.ui.common.FilePickerHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.io.File;
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
        } else if (id == R.id.menu_chat_export) {
            chooseChatExportFormat();
            return true;
        } else if (id == R.id.menu_chat_clear) {
            confirmClearChat();
            return true;
        }
        return false;
    }

    private void chooseChatExportFormat() {
        String[] formats = {getString(R.string.chat_export_json), getString(R.string.chat_export_txt),
                getString(R.string.chat_export_pdf)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chat_export)
                .setItems(formats, (dialog, which) -> exportChat(which == 0
                        ? ExportDataUseCase.ExportType.CHAT_JSON
                        : which == 1 ? ExportDataUseCase.ExportType.CHAT_TXT
                        : ExportDataUseCase.ExportType.CHAT_PDF))
                .show();
    }

    /** 在后台生成聊天记录，并交给系统分享。 */
    private void exportChat(ExportDataUseCase.ExportType type) {
        RolePlayChatApp app = (RolePlayChatApp) requireActivity().getApplication();
        app.container().executors.diskIO().execute(() -> {
            LocalAssetStore assetStore = app.container().assetStore;
            String extension = type == ExportDataUseCase.ExportType.CHAT_JSON ? ".json"
                    : type == ExportDataUseCase.ExportType.CHAT_TXT ? ".txt" : ".pdf";
            String mime = type == ExportDataUseCase.ExportType.CHAT_JSON ? "application/json"
                    : type == ExportDataUseCase.ExportType.CHAT_TXT ? "text/plain" : "application/pdf";
            File target = new File(assetStore.exportsDir(),
                    "chat_" + System.currentTimeMillis() + extension);
            AppError error = app.container().exportDataUseCase.execute(
                    type, scriptId, null, target, false);
            app.container().executors.mainThread().execute(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                if (error != null) {
                    Toast.makeText(requireContext(),
                            getString(R.string.import_export_export_failed, error.getMessage()),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        requireContext(), requireContext().getPackageName() + ".fileprovider", target);
                FilePickerHelper.shareFile(requireActivity(), uri, mime);
            });
        });
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

    private void navigateToAppearance() {
        androidx.navigation.NavController navController =
                androidx.navigation.Navigation.findNavController(requireView());
        Bundle args = new Bundle();
        args.putString("scriptId", scriptId);
        navController.navigate(R.id.action_chat_to_appearance, args);
    }

    private void confirmClearChat() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_chat)
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
        adapter = new ChatMessageAdapter(this::mentionCharacter);
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
