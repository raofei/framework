package com.openerp.orm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.openerp.base.ir.Ir_model;
import com.openerp.orm.OEM2MIds.Operation;
import com.openerp.support.OEUser;

public abstract class OEDatabase extends OESQLiteHelper implements OEDBHelper {
	public static final String TAG = "com.openerp.orm.OEDatabase";
	Context mContext = null;
	OEDBHelper mDBHelper = null;
	OEUser mUser = null;

	public OEDatabase(Context context) {
		super(context);
		mContext = context;
		mUser = OEUser.current(mContext);
		mDBHelper = this;
	}

	public String modelName() {
		return mDBHelper.getModelName();
	}

	public String tableName() {
		return mDBHelper.getModelName().replaceAll("\\.", "_");
	}

	public int count() {
		return count(null, null);
	}

	public int count(String where, String[] whereArgs) {
		int count = 0;
		if (where == null) {
			where = " oea_name = ?";
			whereArgs = new String[] { mUser.getAndroidName() };
		} else {
			where += " and oea_name = ?";
			List<String> tmpWhereArgs = new ArrayList<String>();
			tmpWhereArgs.addAll(Arrays.asList(whereArgs));
			tmpWhereArgs.add(mUser.getAndroidName());
			whereArgs = tmpWhereArgs.toArray(new String[tmpWhereArgs.size()]);
		}
		SQLiteDatabase db = getReadableDatabase();
		Cursor cr = db.query(tableName(), new String[] { "count(*) as total" },
				where, whereArgs, null, null, null);
		if (cr.moveToFirst()) {
			count = cr.getInt(0);
		}
		cr.close();
		db.close();
		return count;
	}

	public int update(OEValues values, int id) {
		return update(values, "id = ?", new String[] { id + "" });
	}

	public int update(OEValues values, String where, String[] whereArgs) {
		if (where == null) {
			where = " oea_name = ?";
			whereArgs = new String[] { mUser.getAndroidName() };
		} else {
			where += " and oea_name = ?";
			List<String> tmpWhereArgs = new ArrayList<String>();
			tmpWhereArgs.addAll(Arrays.asList(whereArgs));
			tmpWhereArgs.add(mUser.getAndroidName());
			whereArgs = tmpWhereArgs.toArray(new String[tmpWhereArgs.size()]);
		}
		if (!values.contains("oea_name")) {
			values.put("oea_name", mUser.getAndroidName());
		}
		SQLiteDatabase db = getWritableDatabase();
		HashMap<String, Object> res = getContentValues(values);
		ContentValues cValues = (ContentValues) res.get("cValues");
		int count = db.update(tableName(), cValues, where, whereArgs);
		db.close();
		if (res.containsKey("m2mObject")) {
			OEDBHelper m2mDb = (OEDBHelper) res.get("m2mObject");
			for (OEDataRow row : select(where, whereArgs, null, null, null)) {
				manageMany2ManyRecords(m2mDb, row.getInt("id"),
						res.get("m2mRecordsObj"));
			}

		}
		return count;

	}

	public List<Long> createORReplace(List<OEValues> listValues) {
		List<Long> ids = new ArrayList<Long>();
		for (OEValues values : listValues) {
			long id = values.getInt("id");
			int count = count("id = ?", new String[] { values.getString("id") });
			if (count == 0) {
				ids.add(id);
				create(values);
			} else {
				ids.add(id);
				update(values, values.getInt("id"));
			}
		}
		return ids;
	}

	public long create(OEValues values) {
		long newId = 0;
		if (!values.contains("oea_name")) {
			values.put("oea_name", mUser.getAndroidName());
		}
		SQLiteDatabase db = getWritableDatabase();
		HashMap<String, Object> res = getContentValues(values);
		ContentValues cValues = (ContentValues) res.get("cValues");
		newId = db.insert(tableName(), null, cValues);
		if (res.containsKey("m2mObject")) {
			OEDBHelper m2mDb = (OEDBHelper) res.get("m2mObject");
			manageMany2ManyRecords(m2mDb, newId, res.get("m2mRecordsObj"));
		}
		db.close();
		return newId;
	}

	private HashMap<String, Object> getContentValues(OEValues values) {
		HashMap<String, Object> result = new HashMap<String, Object>();
		ContentValues cValues = new ContentValues();
		for (String key : values.keys()) {
			if (values.get(key) instanceof OEM2MIds
					|| values.get(key) instanceof List) {
				OEDBHelper m2mDb = findFieldModel(key);
				result.put("m2mObject", m2mDb);
				result.put("m2mRecordsObj", values.get(key));
				continue;
			}
			cValues.put(key, values.get(key).toString());
		}
		result.put("cValues", cValues);
		return result;
	}

	@SuppressWarnings("unchecked")
	private void manageMany2ManyRecords(OEDBHelper relDb, long id, Object idsObj) {
		String first_table = tableName();
		String second_table = relDb.getModelName().replaceAll("\\.", "_");
		String rel_table = first_table + "_" + second_table + "_rel";
		List<Integer> ids = new ArrayList<Integer>();
		Operation operation = Operation.ADD;
		if (idsObj instanceof OEM2MIds) {
			OEM2MIds idsObject = (OEM2MIds) idsObj;
			operation = idsObject.getOperation();
			ids = idsObject.getIds();
		}
		if (idsObj instanceof List) {
			ids = (List<Integer>) idsObj;
		}
		SQLiteDatabase db = null;
		String col_first = first_table + "_id";
		String col_second = second_table + "_id";
		if (operation == Operation.REPLACE) {
			db = getWritableDatabase();
			db.delete(rel_table, col_first + " = ? AND oea_name = ?",
					new String[] { id + "", mUser.getAndroidName() });
			db.close();
		}

		for (Integer rId : ids) {
			ContentValues values = new ContentValues();
			values.put(col_first, id);
			values.put(col_second, rId);
			values.put("oea_name", mUser.getAndroidName());
			switch (operation) {
			case ADD:
			case APPEND:
			case REPLACE:
				Log.d(TAG,
						"createMany2ManyRecords() ADD, APPEND, REPLACE called");
				if (!hasRecord(rel_table, col_first + " = ? AND " + col_second
						+ " = ? AND oea_name = ?", new String[] { id + "",
						rId + "", mUser.getAndroidName() })) {
					db = getWritableDatabase();
					db.insert(rel_table, null, values);
					db.close();
				}
				break;
			case REMOVE:
				Log.d(TAG, "createMany2ManyRecords() REMOVE called");
				db = getWritableDatabase();
				db.delete(rel_table, col_first + " = ? AND " + col_second
						+ " = ? AND oea_name = ?", new String[] { id + "",
						rId + "", mUser.getAndroidName() });
				db.close();
				break;
			}
		}
	}

	private boolean hasRecord(String table, String where, String[] whereArgs) {
		boolean flag = false;
		if (where == null) {
			where = " oea_name = ?";
			whereArgs = new String[] { mUser.getAndroidName() };
		} else {
			where += " and oea_name = ?";
			List<String> tmpWhereArgs = new ArrayList<String>();
			tmpWhereArgs.addAll(Arrays.asList(whereArgs));
			tmpWhereArgs.add(mUser.getAndroidName());
			whereArgs = tmpWhereArgs.toArray(new String[tmpWhereArgs.size()]);
		}
		SQLiteDatabase db = getReadableDatabase();
		Cursor cr = db.query(table, new String[] { "count(*) as total" },
				where, whereArgs, null, null, null);
		cr.moveToFirst();
		int count = cr.getInt(0);
		cr.close();
		db.close();
		if (count > 0) {
			flag = true;
		}
		return flag;
	}

	private OEDBHelper findFieldModel(String field) {
		for (OEColumn col : mDBHelper.getModelColumns()) {
			if (field.equals(col.getName())) {
				OEManyToMany m2m = (OEManyToMany) col.getType();
				return m2m.getDBHelper();
			}
		}
		return null;
	}

	public int delete() {
		return delete(null, null);
	}

	public int delete(String table) {
		return delete(table, null, null);
	}

	public int delete(int id) {
		return delete("id = ?", new String[] { id + "" });
	}

	public int delete(String where, String[] whereArgs) {
		return delete(tableName(), where, whereArgs);
	}

	public int delete(String table, String where, String[] whereArgs) {
		if (where == null) {
			where = "oea_name = ?";
			whereArgs = new String[] { mUser.getAndroidName() };
		} else {
			where += " AND oea_name = ?";
			List<String> tmpWhereArgs = new ArrayList<String>();
			tmpWhereArgs.addAll(Arrays.asList(whereArgs));
			tmpWhereArgs.add(mUser.getAndroidName());
			whereArgs = tmpWhereArgs.toArray(new String[tmpWhereArgs.size()]);
		}
		SQLiteDatabase db = getWritableDatabase();
		int count = db.delete(table, where, whereArgs);
		db.close();
		return count;
	}

	public List<OEDataRow> select() {
		return select(null, null, null, null, null);
	}

	public OEDataRow select(int id) {
		List<OEDataRow> rows = select("id = ?", new String[] { id + "" }, null,
				null, null);
		if (rows.size() > 0) {
			return rows.get(0);
		}
		return null;
	}

	public List<OEDataRow> select(String where, String[] whereArgs) {
		return select(where, whereArgs, null, null, null);
	}

	public List<OEDataRow> select(String where, String[] whereArgs,
			String groupBy, String having, String orderBy) {
		if (where == null) {
			where = "oea_name = ?";
			whereArgs = new String[] { mUser.getAndroidName() };
		} else {
			where += " AND oea_name = ?";
			List<String> tmpWhereArgs = new ArrayList<String>();
			tmpWhereArgs.addAll(Arrays.asList(whereArgs));
			tmpWhereArgs.add(mUser.getAndroidName());
			whereArgs = tmpWhereArgs.toArray(new String[tmpWhereArgs.size()]);
		}
		List<OEDataRow> rows = new ArrayList<OEDataRow>();
		SQLiteDatabase db = getReadableDatabase();
		String[] cols = getColumns();
		Cursor cr = db.query(tableName(), cols, where, whereArgs, groupBy,
				having, orderBy);
		List<OEColumn> mCols = mDBHelper.getModelColumns();
		mCols.addAll(getDefaultCols());
		if (cr.moveToFirst()) {
			do {
				OEDataRow row = new OEDataRow();
				for (OEColumn col : mCols) {
					row.put(col.getName(), createRowData(col, cr));
				}
				rows.add(row);
			} while (cr.moveToNext());
		}
		cr.close();
		db.close();
		return rows;
	}

	public List<OEDataRow> selectM2M(OEDBHelper rel_db, String where,
			String[] whereArgs) {
		if (where == null) {
			where = "oea_name = ?";
			whereArgs = new String[] { mUser.getAndroidName() };
		} else {
			where += " AND oea_name = ?";
			List<String> tmpWhereArgs = new ArrayList<String>();
			tmpWhereArgs.addAll(Arrays.asList(whereArgs));
			tmpWhereArgs.add(mUser.getAndroidName());
			whereArgs = tmpWhereArgs.toArray(new String[tmpWhereArgs.size()]);
		}
		List<OEDataRow> rows = new ArrayList<OEDataRow>();
		HashMap<String, Object> mRelObj = relTableColumns(rel_db);
		@SuppressWarnings("unchecked")
		List<OEColumn> mCols = (List<OEColumn>) mRelObj.get("columns");
		List<String> cols = new ArrayList<String>();
		for (OEColumn col : mCols) {
			cols.add(col.getName());
		}
		SQLiteDatabase db = getReadableDatabase();
		Cursor cr = db.query(mRelObj.get("rel_table").toString(),
				cols.toArray(new String[cols.size()]), where, whereArgs, null,
				null, null);
		OEDatabase rel_db_obj = (OEDatabase) rel_db;
		String rel_col_name = rel_db_obj.tableName() + "_id";
		if (cr.moveToFirst()) {
			do {
				int id = cr.getInt(cr.getColumnIndex(rel_col_name));
				rows.add(rel_db_obj.select(id));
			} while (cr.moveToNext());
		}
		cr.close();
		db.close();
		return rows;
	}

	private Object createRowData(OEColumn col, Cursor cr) {
		if (col.getType() instanceof String) {
			return cr.getString(cr.getColumnIndex(col.getName()));
		}
		if (col.getType() instanceof OEManyToOne) {
			return new OEM2ORecord(col, cr.getString(cr.getColumnIndex(col
					.getName())));
		}
		if (col.getType() instanceof OEManyToMany) {
			return new OEM2MRecord(this, col,
					cr.getInt(cr.getColumnIndex("id")));
		}
		return null;
	}

	private String[] getColumns() {
		List<String> cols = new ArrayList<String>();
		cols.add("id");
		for (OEColumn col : mDBHelper.getModelColumns()) {
			if (col.getType() instanceof String
					|| col.getType() instanceof OEManyToOne) {
				cols.add(col.getName());
			}
		}
		cols.add("oea_name");
		return cols.toArray(new String[cols.size()]);
	}

	public OEHelper getOEInstance() {
		OEHelper openerp = null;
		try {
			openerp = new OEHelper(mContext, mUser, this);
		} catch (Exception e) {
			Log.d(TAG, "OEDatabase->getOEInstance()");
			Log.e(TAG, e.getMessage() + ". No connection with OpenERP server");
		}
		return openerp;
	}

	public boolean isInstalledOnServer() {
		OEHelper oe = getOEInstance();
		boolean installed = false;
		if (oe != null) {
			installed = oe.isModuleInstalled(getModelName());
		} else {
			Ir_model ir = new Ir_model(mContext);
			List<OEDataRow> rows = ir.select("model = ?",
					new String[] { getModelName() });
			if (rows.size() > 0) {
				installed = rows.get(0).getBoolean("is_installed");
			}
		}
		return installed;
	}

	public boolean truncateTable(String table) {
		if (delete(table) > 0) {
			return true;
		}
		return false;
	}

	public boolean truncateTable() {
		if (delete() > 0) {
			return true;
		}
		return false;
	}

	public boolean isEmptyTable() {
		boolean flag = true;
		if (count() > 0) {
			flag = false;
		}
		return flag;
	}

	public HashMap<String, Object> relTableColumns(OEDBHelper relDB) {
		List<OEColumn> mCols = new ArrayList<OEColumn>();
		HashMap<String, Object> res = new HashMap<String, Object>();
		String main_table = tableName();
		String ref_table = relDB.getModelName().replaceAll("\\.", "_");
		String rel_table = main_table + "_" + ref_table + "_rel";
		res.put("rel_table", rel_table);
		mCols.add(new OEColumn(main_table + "_id", "Main ID", OEFields
				.integer()));
		mCols.add(new OEColumn(ref_table + "_id", "Ref ID", OEFields.integer()));
		mCols.add(new OEColumn("oea_name", "Android name", OEFields.text()));
		res.put("columns", mCols);
		return res;
	}

	public List<OEColumn> getDefaultCols() {
		List<OEColumn> cols = new ArrayList<OEColumn>();
		cols.add(new OEColumn("id", "id", OEFields.integer()));
		cols.add(new OEColumn("oea_name", "android name", OEFields.varchar(50)));
		return cols;
	}

	public List<OEColumn> getDatabaseColumns() {
		return mDBHelper.getModelColumns();
	}

	public List<OEColumn> getDatabaseServerColumns() {
		List<OEColumn> cols = new ArrayList<OEColumn>();
		for (OEColumn col : mDBHelper.getModelColumns()) {
			if (col.canSync()) {
				cols.add(col);
			}
		}
		return cols;
	}

	public String getModelName() {
		return mDBHelper.getModelName();
	}
}
