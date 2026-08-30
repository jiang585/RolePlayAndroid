package com.example.roleplaychat.ui.character;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * 角色管理页（S5 列表部分）。
 */
public class CharacterListFragment extends Fragment
        implements CharacterListAdapter.Listener, CharacterListAdapter.OnCharacterLongClickListener {

    private String scriptId;
    private CharacterListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_character_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
        }
        RecyclerView recyclerView = view.findViewById(R.id.recycler_characters);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CharacterListAdapter(this, this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_character);
        fab.setOnClickListener(v -> navigateToEdit(null));

        ((RolePlayChatApp) requireActivity().getApplication()).container()
                .characterRepository.observeByScriptId(scriptId)
                .observe(getViewLifecycleOwner(), this::render);
    }

    private void render(List<CharacterProfile> characters) {
        adapter.submitList(characters);
    }

    @Override
    public void onCharacterClick(CharacterProfile character) {
        navigateToEdit(character.getId());
    }

    @Override
    public void onCharacterLongClick(CharacterProfile character) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(character.getName())
                .setItems(new CharSequence[]{
                        getString(R.string.character_delete_menu),
                        getString(R.string.character_disable_menu)
                }, (dialog, which) -> {
                    if (which == 0) {
                        confirmDelete(character);
                    } else {
                        disableCharacter(character);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDelete(CharacterProfile character) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.character_delete_menu)
                .setMessage(getString(R.string.character_delete_confirm, character.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        deleteCharacter(character))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteCharacter(CharacterProfile character) {
        com.example.roleplaychat.domain.repository.CharacterRepository repository =
                ((RolePlayChatApp) requireActivity().getApplication()).container().characterRepository;
        ((RolePlayChatApp) requireActivity().getApplication()).container().executors
                .diskIO().execute(() -> {
                    repository.deleteCharacter(character.getId());
                    toastOnMain(R.string.character_deleted);
                });
    }

    private void disableCharacter(CharacterProfile character) {
        com.example.roleplaychat.domain.repository.CharacterRepository repository =
                ((RolePlayChatApp) requireActivity().getApplication()).container().characterRepository;
        ((RolePlayChatApp) requireActivity().getApplication()).container().executors
                .diskIO().execute(() -> {
                    repository.disableCharacter(character.getId());
                    toastOnMain(R.string.character_disabled);
                });
    }

    private void toastOnMain(int resId) {
        ((RolePlayChatApp) requireActivity().getApplication()).container().executors
                .mainThread().execute(() -> {
                    if (isAdded() && getContext() != null) {
                        android.widget.Toast.makeText(getContext(), resId,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToEdit(@Nullable String characterId) {
        Bundle args = new Bundle();
        args.putString("scriptId", scriptId);
        if (characterId != null) {
            args.putString("characterId", characterId);
        }
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_characterList_to_characterEdit, args);
    }
}
