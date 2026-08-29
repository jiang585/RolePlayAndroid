package com.example.roleplaychat.ui.script;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.ui.character.CharacterListAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

        MaterialButton identityButton = view.findViewById(R.id.btn_choose_identity);
        identityButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_identityChooser));

        MaterialButton appearanceButton = view.findViewById(R.id.btn_appearance);
        appearanceButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_appearance));

        MaterialButton characterManageButton = view.findViewById(R.id.btn_manage_characters);
        characterManageButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_characterList));

        MaterialButton importButton = view.findViewById(R.id.btn_import);
        importButton.setOnClickListener(v -> navigate(R.id.action_scriptDetail_to_characterImport));

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
        }
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
