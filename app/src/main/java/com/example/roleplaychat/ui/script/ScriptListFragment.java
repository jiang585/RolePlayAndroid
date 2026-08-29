package com.example.roleplaychat.ui.script;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * 剧本列表页（S1）。
 */
public class ScriptListFragment extends Fragment implements ScriptListAdapter.Listener {

    private ScriptListViewModel viewModel;
    private ScriptListAdapter adapter;
    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_script_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(ScriptListViewModel.class);

        recyclerView = view.findViewById(R.id.recycler_scripts);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ScriptListAdapter(this);
        recyclerView.setAdapter(adapter);

        fabAdd = view.findViewById(R.id.fab_add_script);
        fabAdd.setOnClickListener(v -> showCreateDialog());

        viewModel.getScripts().observe(getViewLifecycleOwner(), this::renderScripts);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof com.example.roleplaychat.MainActivity) {
            ((com.example.roleplaychat.MainActivity) getActivity()).refreshScriptListToolbar();
        }
    }

    @Override
    public void onDestroyView() {
        if (getActivity() != null) {
            com.google.android.material.appbar.MaterialToolbar toolbar =
                    getActivity().findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.getMenu().clear();
                toolbar.setOnMenuItemClickListener(null);
            }
        }
        super.onDestroyView();
    }

    private void renderScripts(List<Script> scripts) {
        adapter.submitList(scripts);
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.startsWith("created:")) {
            String id = value.substring("created:".length());
            Toast.makeText(requireContext(), R.string.script_created, Toast.LENGTH_SHORT).show();
            NavController navController = Navigation.findNavController(requireView());
            Bundle args = new Bundle();
            args.putString("scriptId", id);
            navController.navigate(R.id.action_scriptList_to_scriptDetail, args);
        } else if (value.startsWith("error:")) {
            Toast.makeText(requireContext(), value.substring("error:".length()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showCreateDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_script, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_script_name);
        TextInputEditText lineInput = dialogView.findViewById(R.id.input_script_one_line);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.script_create)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = nameInput.getText() == null ? "" : nameInput.getText().toString();
                    String line = lineInput.getText() == null ? "" : lineInput.getText().toString();
                    viewModel.createScript(name, line, System.currentTimeMillis());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onScriptClick(Script script) {
        NavController navController = Navigation.findNavController(requireView());
        Bundle args = new Bundle();
        args.putString("scriptId", script.getId());
        navController.navigate(R.id.action_scriptList_to_scriptDetail, args);
    }

    @Override
    public void onScriptLongClick(Script script) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_delete)
                .setMessage(getString(R.string.script_delete_confirm, script.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        viewModel.deleteScript(script))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
