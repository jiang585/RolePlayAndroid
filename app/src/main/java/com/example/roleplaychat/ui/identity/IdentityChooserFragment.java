package com.example.roleplaychat.ui.identity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.PlayerIdentity;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * 身份选择页（S7）。
 */
public class IdentityChooserFragment extends Fragment {

    private String scriptId;
    private IdentityViewModel viewModel;
    private RadioGroup roleGroup;
    private ChipGroup characterChips;
    private MaterialButton confirmButton;
    private String selectedCharacterId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_identity_chooser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
        }
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(IdentityViewModel.class);
        viewModel.setScriptId(scriptId);

        roleGroup = view.findViewById(R.id.radio_identity_role);
        characterChips = view.findViewById(R.id.chip_group_characters);
        confirmButton = view.findViewById(R.id.btn_confirm_identity);

        roleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean needsCharacter = checkedId == R.id.radio_protagonist
                    || checkedId == R.id.radio_supporting;
            characterChips.setVisibility(needsCharacter ? View.VISIBLE : View.GONE);
        });

        confirmButton.setOnClickListener(v -> {
            int checkedId = roleGroup.getCheckedRadioButtonId();
            PlayerIdentity.RoleType roleType;
            if (checkedId == R.id.radio_protagonist) {
                roleType = PlayerIdentity.RoleType.PROTAGONIST;
            } else if (checkedId == R.id.radio_supporting) {
                roleType = PlayerIdentity.RoleType.SUPPORTING;
            } else {
                roleType = PlayerIdentity.RoleType.OBSERVER;
            }
            viewModel.switchIdentity(roleType, selectedCharacterId);
        });

        viewModel.getEnabledCharacters().observe(getViewLifecycleOwner(), this::renderCharacters);
        viewModel.getCurrentIdentity().observe(getViewLifecycleOwner(), this::renderCurrent);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
    }

    private void renderCharacters(List<CharacterProfile> characters) {
        characterChips.removeAllViews();
        selectedCharacterId = null;
        if (characters == null || characters.isEmpty()) {
            return;
        }
        for (CharacterProfile character : characters) {
            Chip chip = new Chip(requireContext());
            chip.setText(character.getName());
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCharacterId = character.getId();
                }
            });
            characterChips.addView(chip);
        }
    }

    private void renderCurrent(PlayerIdentity identity) {
        if (identity == null) {
            roleGroup.check(R.id.radio_observer);
            return;
        }
        switch (identity.getRoleType()) {
            case PROTAGONIST:
                roleGroup.check(R.id.radio_protagonist);
                break;
            case SUPPORTING:
                roleGroup.check(R.id.radio_supporting);
                break;
            case OBSERVER:
            default:
                roleGroup.check(R.id.radio_observer);
                break;
        }
        if (identity.getCharacterId() != null) {
            for (int i = 0; i < characterChips.getChildCount(); i++) {
                View child = characterChips.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    if (chip.getTag() != null && identity.getCharacterId().equals(chip.getTag())) {
                        chip.setChecked(true);
                    }
                }
            }
        }
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.equals("saved")) {
            Toast.makeText(requireContext(), R.string.identity_saved, Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        } else if (value.startsWith("error:")) {
            Toast.makeText(requireContext(), value.substring("error:".length()),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
