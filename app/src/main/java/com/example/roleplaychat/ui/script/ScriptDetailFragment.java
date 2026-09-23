package com.example.roleplaychat.ui.script;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.AppContainer;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.domain.model.CharacterVisualProfile;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.ui.character.CharacterListAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 剧本详情页（S3）：世界观概览、角色成员、进入群聊、装扮、身份、导入导出入口。
 */
public class ScriptDetailFragment extends Fragment {

    private String scriptId;
    private AppContainer container;
    private TextView worldSummary;
    private TextView identityText;
    private RecyclerView characterList;
    private CharacterListAdapter characterAdapter;
    private View characterEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_script_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
        }
        this.container = ((RolePlayChatApp) requireActivity().getApplication()).container();

        worldSummary = view.findViewById(R.id.tv_world_summary);
        identityText = view.findViewById(R.id.tv_identity_summary);
        characterList = view.findViewById(R.id.recycler_characters);
        characterEmpty = view.findViewById(R.id.character_empty);
        characterList.setLayoutManager(new LinearLayoutManager(requireContext()));
        characterAdapter = new CharacterListAdapter(character -> navigateToCharacterEdit(character.getId()));
        characterList.setAdapter(characterAdapter);

        MaterialCardView worldCard = view.findViewById(R.id.card_world);
        worldCard.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_worldEdit));

        MaterialButton chatButton = view.findViewById(R.id.btn_enter_chat);
        chatButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_chat));
        view.findViewById(R.id.btn_script_media_mode).setOnClickListener(v -> showMediaModeDialog());

        MaterialButton identityButton = view.findViewById(R.id.btn_choose_identity);
        identityButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_identityChooser));

        MaterialButton appearanceButton = view.findViewById(R.id.btn_appearance);
        appearanceButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_appearance));

        MaterialButton characterManageButton = view.findViewById(R.id.btn_manage_characters);
        characterManageButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_characterList));

        MaterialButton dataTransferButton = view.findViewById(R.id.btn_export_script);
        dataTransferButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_export));

        observe();
    }

    private void observe() {
        container.scriptRepository.observeById(scriptId)
                .observe(getViewLifecycleOwner(), this::renderScript);
        container.worldRepository.observeByScriptId(scriptId)
                .observe(getViewLifecycleOwner(), this::renderWorld);
        container.characterRepository.observeByScriptId(scriptId)
                .observe(getViewLifecycleOwner(), this::renderCharacters);
        container.scriptRepository.observePlayerIdentity(scriptId)
                .observe(getViewLifecycleOwner(), this::renderIdentity);
    }

    private void renderScript(Script script) {
        if (script != null && getView() != null) {
            TextView title = getView().findViewById(R.id.tv_script_name);
            title.setText(script.getName());
            MaterialButton modeButton = getView().findViewById(R.id.btn_script_media_mode);
            modeButton.setText(script.isVisual() ? "剧本图片模式：有图" : "剧本图片模式：无图");
        }
    }

    private void showMediaModeDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("剧本图片模式")
                .setItems(new String[]{"无图剧本（只聊天）", "有图剧本（角色可发照片）"},
                        (dialog, selected) -> {
                            if (selected == 0) {
                                container.executors.diskIO().execute(() -> container.scriptRepository.setMediaMode(
                                        scriptId, Script.MediaMode.TEXT_ONLY, System.currentTimeMillis()));
                            } else {
                                enableVisualMode();
                            }
                        })
                .show();
    }

    private void enableVisualMode() {
        container.executors.diskIO().execute(() -> {
            List<CharacterProfile> characters = container.characterRepository.getEnabledByScriptId(scriptId);
            for (CharacterProfile character : characters) {
                CharacterVisualProfile profile = container.characterVisualRepository.getByCharacterId(character.getId());
                if (profile == null || !profile.isReady()
                        || profile.getAppearanceJson() == null
                        || profile.getAppearanceJson().contains("身高：；")
                        || profile.getAppearanceJson().endsWith("体型：")) {
                    container.executors.mainThread().execute(() -> Toast.makeText(requireContext(),
                            "请先为角色「" + character.getName() + "」上传正脸并填写身高体型", Toast.LENGTH_LONG).show());
                    return;
                }
            }
            container.scriptRepository.setMediaMode(scriptId, Script.MediaMode.VISUAL, System.currentTimeMillis());
            container.executors.mainThread().execute(() -> Toast.makeText(requireContext(),
                    "已启用有图剧本", Toast.LENGTH_SHORT).show());
        });
    }

    private void renderWorld(WorldSetting world) {
        if (world == null) {
            worldSummary.setText(R.string.script_detail_no_world);
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (world.getEra() != null && !world.getEra().isEmpty()) {
            sb.append(world.getEra());
        }
        if (world.getLocation() != null && !world.getLocation().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(world.getLocation());
        }
        if (world.getStoryHook() != null && !world.getStoryHook().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("主线：").append(world.getStoryHook());
        }
        worldSummary.setText(sb.length() == 0
                ? getString(R.string.script_detail_no_world) : sb.toString());
    }

    private void renderCharacters(List<CharacterProfile> characters) {
        characterAdapter.submitList(characters);
        characterEmpty.setVisibility(characters == null || characters.isEmpty()
                ? View.VISIBLE : View.GONE);
    }

    private void renderIdentity(PlayerIdentity identity) {
        if (identity == null) {
            identityText.setText(R.string.identity_no_character);
            return;
        }
        String label;
        switch (identity.getRoleType()) {
            case PROTAGONIST:
                label = getString(R.string.identity_protagonist);
                break;
            case SUPPORTING:
                label = getString(R.string.identity_supporting);
                break;
            case OBSERVER:
            default:
                label = getString(R.string.identity_observer);
                break;
        }
        if (!identity.isObserver() && identity.getCharacterId() != null) {
            String finalLabel = label;
            container.executors.diskIO().execute(() -> {
                CharacterProfile profile = container.characterRepository.getById(identity.getCharacterId());
                String name = profile == null ? "?" : profile.getName();
                container.executors.mainThread().execute(() ->
                        identityText.setText(finalLabel + " · " + name));
            });
        } else {
            identityText.setText(label);
        }
    }

    private void navigateToCharacterEdit(String characterId) {
        Bundle args = new Bundle();
        args.putString("scriptId", scriptId);
        args.putString("characterId", characterId);
        navigate(R.id.action_scriptDetail_to_characterEdit, args);
    }

    private void navigate(int actionId) {
        navigate(actionId, null);
    }

    private void navigate(int actionId, @Nullable Bundle args) {
        NavController navController = Navigation.findNavController(requireView());
        if (args == null) {
            args = new Bundle();
        }
        args.putString("scriptId", scriptId);
        navController.navigate(actionId, args);
    }
}
