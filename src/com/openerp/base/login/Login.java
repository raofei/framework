/*
 * OpenERP, Open Source Management Solution
 * Copyright (C) 2012-today OpenERP SA (<http:www.openerp.com>)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 * 
 */
package com.openerp.base.login;

import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.openerp.R;
import com.openerp.auth.OpenERPAccountManager;
import com.openerp.orm.OEHelper;
import com.openerp.support.AppScope;
import com.openerp.support.BaseFragment;
import com.openerp.support.JSONDataHelper;
import com.openerp.support.OEDialog;
import com.openerp.support.OEUser;
import com.openerp.util.controls.OEEditText;
import com.openerp.util.drawer.DrawerItem;

/**
 * The Class Login.
 */
public class Login extends BaseFragment {

	/** The item arr. */
	String[] itemArr = null;

	/** The context. */
	Context context = null;

	/** The m action mode. */
	ActionMode mActionMode;

	/** The open erp server url. */
	String openERPServerURL = "";

	/** The edt server url. */
	OEEditText edtServerUrl = null;

	/** The arguments. */
	Bundle arguments = null;

	/** The db list spinner. */
	Spinner dbListSpinner = null;

	/** The root view. */
	View rootView = null;

	/** The login user a sync. */
	LoginUser loginUserASync = null;

	/** The edt username. */
	OEEditText edtUsername = null;

	/** The edt password. */
	OEEditText edtPassword = null;

	/** The OpenERP Object */
	OEHelper openerp = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater,
	 * android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		this.context = getActivity();
		scope = new AppScope(this);
		// Inflate the layout for this fragment
		rootView = inflater.inflate(R.layout.fragment_login, container, false);
		dbListSpinner = (Spinner) rootView.findViewById(R.id.lstDatabases);
		this.handleArguments((Bundle) getArguments());
		this.loadDatabaseList();
		getActivity().setTitle("Login");
		getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
		getActivity().getActionBar().setHomeButtonEnabled(false);
		return rootView;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.openerp.support.FragmentHelper#handleArguments(android.os.Bundle)
	 */
	public void handleArguments(Bundle bundle) {
		arguments = bundle;
		if (arguments != null && arguments.size() > 0) {
			if (arguments.containsKey("openERPServerURL")) {
				openERPServerURL = arguments.getString("openERPServerURL");
			}
		}
	}

	/**
	 * Load database list.
	 */
	private void loadDatabaseList() {
		try {
			openerp = new OEHelper(context, openERPServerURL);
			List<String> dbList = new JSONDataHelper()
					.arrayToStringList(openerp.getDatabaseList());
			dbList.add(0,
					getActivity().getString(R.string.login_select_database));
			ArrayAdapter<String> dbAdapter = new ArrayAdapter<String>(
					getActivity(), R.layout.spinner_custom_layout, dbList);
			dbAdapter.setDropDownViewResource(R.layout.spinner_custom_layout);
			dbListSpinner.setAdapter(dbAdapter);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.support.v4.app.Fragment#onCreateOptionsMenu(android.view.Menu,
	 * android.view.MenuInflater)
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_fragment_login, menu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.support.v4.app.Fragment#onOptionsItemSelected(android.view.MenuItem
	 * )
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// handle item selection

		switch (item.getItemId()) {
		case R.id.menu_login_account:
			Log.d("LoginFragment()->ActionBarMenuClicked", "menu_login_account");
			edtUsername = (OEEditText) rootView.findViewById(R.id.edtUsername);
			edtPassword = (OEEditText) rootView.findViewById(R.id.edtPassword);
			edtUsername.setError(null);
			edtPassword.setError(null);
			if (TextUtils.isEmpty(edtUsername.getText())) {
				edtUsername.setError("Provide Username");
			} else if (TextUtils.isEmpty(edtPassword.getText())) {
				edtPassword.setError("Provide Password");
			} else if (dbListSpinner.getSelectedItemPosition() == 0) {
				Toast.makeText(getActivity(), "Please select database",
						Toast.LENGTH_LONG).show();
			} else {
				loginUserASync = new LoginUser();
				loginUserASync.execute((Void) null);
			}

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * The Class LoginUser.
	 */
	private class LoginUser extends AsyncTask<Void, Void, Boolean> {

		/** The pdialog. */
		OEDialog pdialog;

		/** The error msg. */
		String errorMsg = "";

		/** The user data. */
		OEUser userData = null;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPreExecute()
		 */
		@Override
		protected void onPreExecute() {
			pdialog = new OEDialog(getActivity(), false, "Logging in...");
			pdialog.show();
			edtPassword.setError(null);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(Void... params) {
			try {
				// Simulate network access.
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				return false;
			}
			if (openerp != null) {
				String userName = edtUsername.getText().toString();
				String password = edtPassword.getText().toString();
				String database = dbListSpinner.getSelectedItem().toString();

				userData = openerp.login(userName, password, database,
						openERPServerURL);
				if (userData != null) {
					return true;
				} else {
					errorMsg = "Invalid Username or Password !";
					return false;
				}
			}

			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(final Boolean success) {
			if (success) {
				Log.d("Creating Account For Username :",
						userData.getAndroidName());
				if (OpenERPAccountManager.fetchAllAccounts(getActivity()) != null) {
					if (OpenERPAccountManager.isAnyUser(getActivity())) {
						OpenERPAccountManager.logoutUser(getActivity(),
								OpenERPAccountManager
										.currentUser(getActivity())
										.getAndroidName());
					}
				}
				if (OpenERPAccountManager
						.createAccount(getActivity(), userData)) {
					loginUserASync.cancel(true);
					pdialog.hide();
					SyncWizard syncWizard = new SyncWizard();
					scope.main().fragmentHandler.replaceFragmnet(syncWizard);

				}
			} else {
				edtPassword.setError(errorMsg);
			}
			loginUserASync.cancel(true);
			pdialog.hide();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onCancelled()
		 */
		@Override
		protected void onCancelled() {
			loginUserASync.cancel(true);
			pdialog.hide();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.support.v4.app.Fragment#onDestroyView()
	 */
	@Override
	public void onDestroyView() {
		super.onDestroyView();
		rootView = null; // now cleaning up!
	}

	@Override
	public Object databaseHelper(Context context) {
		return null;
	}

	@Override
	public List<DrawerItem> drawerMenus(Context context) {
		return null;
	}
}
