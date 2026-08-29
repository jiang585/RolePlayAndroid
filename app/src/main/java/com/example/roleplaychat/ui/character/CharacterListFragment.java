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
public class CharacterListFragment extends Fragment implements CharacterListAdapter.Listener {

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
        adapter = new CharacterListAdapter(this);
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
