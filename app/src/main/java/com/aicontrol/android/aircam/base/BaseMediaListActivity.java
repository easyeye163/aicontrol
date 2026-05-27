package com.aicontrol.android.aircam.base;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.adapter.BaseMediaAdapter;
import com.aicontrol.android.aircam.utils.GridSpacingItemDecoration;
import com.aicontrol.android.aircam.utils.PxUtils;
import com.aicontrol.android.aircam.view.dialog.AlartDialog;
import java.util.List;

/* loaded from: classes5.dex */
public abstract class BaseMediaListActivity<A extends BaseMediaAdapter> extends BaseActivity {
    protected View bottomActionBar;
    protected Button btnCancel;
    protected Button btnDelete;
    protected Button btnSelectAll;
    protected Button btnShare;
    private boolean isSelectionMode = false;
    protected A mAdapter;
    protected RecyclerView mRecyclerView;
    protected TextView noFileTxt;
    protected MenuItem selectMenuItem;
    protected Toolbar toolbar;

    protected abstract A createAdapter();

    protected abstract int getItemCount();

    protected abstract int getLayoutId();

    protected abstract String getTitleString();

    protected abstract void initData();

    protected abstract void onDeleteSelected(List<Integer> list);

    protected abstract void onShareSelected(List<Integer> list);

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(getLayoutId());
        initViews();
        initData();
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        this.toolbar = toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getTitleString());
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_ios_back);
        }
        this.toolbar.setNavigationOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.base.BaseMediaListActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                BaseMediaListActivity.this.m3893x59c30781(view);
            }
        });
        TextView textView = (TextView) findViewById(R.id.NoFileVlist);
        this.noFileTxt = textView;
        if (textView == null) {
            this.noFileTxt = (TextView) findViewById(R.id.NoFilePlist);
        }
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        this.mRecyclerView = recyclerView;
        recyclerView.setLayoutManager(new GridLayoutManager(this, 6));
        this.mRecyclerView.addItemDecoration(new GridSpacingItemDecoration(6, PxUtils.dpToPx(this, 5), true));
        A createAdapter = createAdapter();
        this.mAdapter = createAdapter;
        this.mRecyclerView.setAdapter(createAdapter);
        this.mAdapter.setOnSelectionChangeListener(new BaseMediaAdapter.OnSelectionChangeListener() { // from class: com.tzh.wifi.wificam.base.BaseMediaListActivity.1
            @Override // com.tzh.wifi.wificam.adapter.BaseMediaAdapter.OnSelectionChangeListener
            public void onSelectionChanged(int i) {
                BaseMediaListActivity.this.updateSelectionUI(i);
            }
        });
        View findViewById = findViewById(R.id.bottomActionBar);
        this.bottomActionBar = findViewById;
        if (findViewById != null) {
            findViewById.setVisibility(8);
        }
        this.btnSelectAll = (Button) findViewById(R.id.btnSelectAll);
        this.btnDelete = (Button) findViewById(R.id.btnDelete);
        this.btnShare = (Button) findViewById(R.id.btnShare);
        this.btnCancel = (Button) findViewById(R.id.btnCancel);
        Button button = this.btnSelectAll;
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.base.BaseMediaListActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BaseMediaListActivity.this.m3894x594ca182(view);
                }
            });
        }
        Button button2 = this.btnDelete;
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.base.BaseMediaListActivity$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BaseMediaListActivity.this.m3895x58d63b83(view);
                }
            });
        }
        Button button3 = this.btnShare;
        if (button3 != null) {
            button3.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.base.BaseMediaListActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BaseMediaListActivity.this.m3896x585fd584(view);
                }
            });
        }
        Button button4 = this.btnCancel;
        if (button4 != null) {
            button4.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.base.BaseMediaListActivity$$ExternalSyntheticLambda4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BaseMediaListActivity.this.m3897x57e96f85(view);
                }
            });
        }
    }

    /* renamed from: lambda$initViews$0$com-tzh-wifi-wificam-base-BaseMediaListActivity, reason: not valid java name */
    /* synthetic */ void m3893x59c30781(View view) {
        onBackPressed();
    }

    /* renamed from: lambda$initViews$1$com-tzh-wifi-wificam-base-BaseMediaListActivity, reason: not valid java name */
    /* synthetic */ void m3894x594ca182(View view) {
        toggleSelectAll();
    }

    /* renamed from: lambda$initViews$2$com-tzh-wifi-wificam-base-BaseMediaListActivity, reason: not valid java name */
    /* synthetic */ void m3895x58d63b83(View view) {
        deleteSelectedItems();
    }

    /* renamed from: lambda$initViews$3$com-tzh-wifi-wificam-base-BaseMediaListActivity, reason: not valid java name */
    /* synthetic */ void m3896x585fd584(View view) {
        shareSelectedItems();
    }

    /* renamed from: lambda$initViews$4$com-tzh-wifi-wificam-base-BaseMediaListActivity, reason: not valid java name */
    /* synthetic */ void m3897x57e96f85(View view) {
        exitSelectionMode();
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        if (this.isSelectionMode) {
            getMenuInflater().inflate(R.menu.menu_selection_mode, menu);
            return true;
        }
        getMenuInflater().inflate(R.menu.menu_file_list, menu);
        this.selectMenuItem = menu.findItem(R.id.action_select);
        return true;
    }

    @Override // android.app.Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.action_select) {
            enterSelectionMode();
            return true;
        }
        if (itemId == R.id.action_delete) {
            deleteSelectedItems();
            return true;
        }
        if (itemId == R.id.action_cancel) {
            exitSelectionMode();
            return true;
        }
        if (itemId == R.id.action_select_all) {
            toggleSelectAll();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    protected void enterSelectionMode() {
        A a = this.mAdapter;
        if (a == null) {
            return;
        }
        this.isSelectionMode = true;
        a.setSelectionMode(true);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Select Items");
        }
        invalidateOptionsMenu();
    }

    protected void exitSelectionMode() {
        A a = this.mAdapter;
        if (a == null) {
            return;
        }
        this.isSelectionMode = false;
        a.setSelectionMode(false);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getTitleString());
        }
        invalidateOptionsMenu();
    }

    protected void toggleSelectAll() {
        A a = this.mAdapter;
        if (a == null) {
            return;
        }
        if (a.getSelectedCount() == getItemCount()) {
            this.mAdapter.clearSelection();
            Button button = this.btnSelectAll;
            if (button != null) {
                button.setText("Select All");
                return;
            }
            return;
        }
        this.mAdapter.selectAll(getItemCount());
        Button button2 = this.btnSelectAll;
        if (button2 != null) {
            button2.setText("Deselect All");
        }
    }

    protected void updateSelectionUI(int i) {
        String str;
        if (getSupportActionBar() != null) {
            ActionBar supportActionBar = getSupportActionBar();
            if (i > 0) {
                str = "Selected " + i;
            } else {
                str = "Select Items";
            }
            supportActionBar.setTitle(str);
        }
    }

    protected void deleteSelectedItems() {
        A a = this.mAdapter;
        if (a == null) {
            return;
        }
        final List<Integer> selectedPositions = a.getSelectedPositions();
        if (selectedPositions.isEmpty()) {
            return;
        }
        AlartDialog alartDialog = new AlartDialog(this, R.style.dialog);
        alartDialog.setTitle("Delete");
        alartDialog.setContent("Delete " + selectedPositions.size() + " items?");
        alartDialog.setAlartClickListener(new AlartDialog.AlartDialogClick() { // from class: com.tzh.wifi.wificam.base.BaseMediaListActivity.2
            @Override // com.tzh.wifi.wificam.view.dialog.AlartDialog.AlartDialogClick
            public void OnCancelClick() {
            }

            @Override // com.tzh.wifi.wificam.view.dialog.AlartDialog.AlartDialogClick
            public void OnConfirmClick() {
                BaseMediaListActivity.this.onDeleteSelected(selectedPositions);
                BaseMediaListActivity.this.exitSelectionMode();
            }
        });
        alartDialog.show();
    }

    protected void shareSelectedItems() {
        A a = this.mAdapter;
        if (a == null) {
            return;
        }
        List<Integer> selectedPositions = a.getSelectedPositions();
        if (selectedPositions.isEmpty()) {
            return;
        }
        onShareSelected(selectedPositions);
    }

    protected void updateEmptyView() {
        if (this.noFileTxt == null) {
            return;
        }
        if (getItemCount() <= 0) {
            this.noFileTxt.setVisibility(0);
        } else {
            this.noFileTxt.setVisibility(8);
        }
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    public void onBackPressed() {
        A a = this.mAdapter;
        if (a != null && a.isSelectionMode()) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        A a = this.mAdapter;
        if (a != null) {
            a.notifyDataSetChanged();
            updateEmptyView();
        }
    }
}
