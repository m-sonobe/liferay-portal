/**
 * Copyright (c) 2000-2009 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portal.upgrade.v5_2_3;

import com.liferay.portal.NoSuchLayoutException;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Layout;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.upgrade.UpgradeException;
import com.liferay.portal.upgrade.UpgradeProcess;
import com.liferay.portal.upgrade.util.DefaultUpgradeTableImpl;
import com.liferay.portal.upgrade.util.UpgradeTable;
import com.liferay.portlet.PortletPreferencesImpl;
import com.liferay.portlet.PortletPreferencesSerializer;
import com.liferay.portlet.documentlibrary.model.impl.DLFileEntryImpl;
import com.liferay.portlet.documentlibrary.model.impl.DLFileRankImpl;
import com.liferay.portlet.documentlibrary.model.impl.DLFileShortcutImpl;
import com.liferay.portlet.documentlibrary.model.impl.DLFileVersionImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * <a href="UpgradeDocumentLibrary.java.html"><b><i>View Source</i></b></a>
 *
 * @author Samuel Kong
 * @author Brian Wing Shun Chan
 * @author Douglas Wong
 *
 */
public class UpgradeDocumentLibrary extends UpgradeProcess {

	public void upgrade() throws UpgradeException {
		_log.info("Upgrading");

		try {
			doUpgrade();
		}
		catch (Exception e) {
			throw new UpgradeException(e);
		}
	}

	protected void deletePortletPreferences(long portletPreferencesId)
		throws Exception {

		Connection con = null;
		PreparedStatement ps = null;

		try {
			con = DataAccess.getConnection();

			ps = con.prepareStatement(
				"delete from PortletPreferences where portletPreferencesId = " +
					portletPreferencesId);

			ps.executeUpdate();
		}
		finally {
			DataAccess.cleanUp(con, ps);
		}
	}

	protected void doUpgrade() throws Exception {
		if (isSupportsAlterColumnName()) {
			runSQL("alter_column_type DLFileEntry name VARCHAR(255) null");
			runSQL("alter_column_type DLFileEntry title VARCHAR(255) null");
			runSQL("alter_column_type DLFileRank name VARCHAR(255) null");
			runSQL("alter_column_type DLFileShortcut toName VARCHAR(255) null");
			runSQL("alter_column_type DLFileVersion name VARCHAR(255) null");
		}
		else {

			// DLFileEntry

			UpgradeTable upgradeTable = new DefaultUpgradeTableImpl(
				DLFileEntryImpl.TABLE_NAME, DLFileEntryImpl.TABLE_COLUMNS);

			upgradeTable.setCreateSQL(DLFileEntryImpl.TABLE_SQL_CREATE);

			upgradeTable.updateTable();

			// DLFileRank

			upgradeTable = new DefaultUpgradeTableImpl(
				DLFileRankImpl.TABLE_NAME, DLFileRankImpl.TABLE_COLUMNS);

			upgradeTable.setCreateSQL(DLFileRankImpl.TABLE_SQL_CREATE);

			upgradeTable.updateTable();

			// DLFileShortcut

			upgradeTable = new DefaultUpgradeTableImpl(
				DLFileShortcutImpl.TABLE_NAME,
				DLFileShortcutImpl.TABLE_COLUMNS);

			upgradeTable.setCreateSQL(DLFileShortcutImpl.TABLE_SQL_CREATE);

			upgradeTable.updateTable();

			// DLFileVersion

			upgradeTable = new DefaultUpgradeTableImpl(
				DLFileVersionImpl.TABLE_NAME, DLFileVersionImpl.TABLE_COLUMNS);

			upgradeTable.setCreateSQL(DLFileVersionImpl.TABLE_SQL_CREATE);

			upgradeTable.updateTable();
		}

		// PortletPreferences

		updatePortletPreferences();
	}

	protected void updatePortletPreferences() throws Exception {
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DataAccess.getConnection();

			ps = con.prepareStatement(
				"select portletPreferencesId, ownerId, ownerType, plid, " +
					"portletId, preferences from PortletPreferences where " +
						"portletId = '20' and preferences like " +
							"'%<name>fileEntryColumns</name><value></value>%'");

			rs = ps.executeQuery();

			while (rs.next()) {
				long portletPreferencesId = rs.getLong("portletPreferencesId");
				long ownerId = rs.getLong("ownerId");
				int ownerType = rs.getInt("ownerType");
				long plid = rs.getLong("plid");
				String portletId = rs.getString("portletId");
				String preferences = rs.getString("preferences");

				try {
					Layout layout = LayoutLocalServiceUtil.getLayout(plid);

					String newPreferences = upgradePreferences(
						layout.getCompanyId(), ownerId, ownerType, plid,
						portletId, preferences);

					updatePortletPreferences(
						portletPreferencesId, newPreferences);
				}
				catch (NoSuchLayoutException nsle) {
					deletePortletPreferences(portletPreferencesId);
				}
			}
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}
	}

	protected void updatePortletPreferences(
			long portletPreferencesId, String preferences)
		throws Exception {

		Connection con = null;
		PreparedStatement ps = null;

		try {
			con = DataAccess.getConnection();

			ps = con.prepareStatement(
				"update PortletPreferences set preferences = ? where " +
					"portletPreferencesId = " + portletPreferencesId);

			ps.setString(1, preferences);

			ps.executeUpdate();
		}
		finally {
			DataAccess.cleanUp(con, ps);
		}
	}

	protected String upgradePreferences(
			long companyId, long ownerId, int ownerType, long plid,
			String portletId, String xml)
		throws Exception {

		PortletPreferencesImpl preferences =
			PortletPreferencesSerializer.fromXML(
				companyId, ownerId, ownerType, plid, portletId, xml);

		String fileEntryColumns = preferences.getValue(
			"fileEntryColumns", StringPool.BLANK);

		if (Validator.isNull(fileEntryColumns)) {
			preferences.reset("fileEntryColumns");
		}

		return PortletPreferencesSerializer.toXML(preferences);
	}

	private static Log _log =
		 LogFactoryUtil.getLog(UpgradeDocumentLibrary.class);

}