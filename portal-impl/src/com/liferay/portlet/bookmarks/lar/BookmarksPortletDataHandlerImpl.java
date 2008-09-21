/**
 * Copyright (c) 2000-2008 Liferay, Inc. All rights reserved.
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

package com.liferay.portlet.bookmarks.lar;

import com.liferay.portal.PortalException;
import com.liferay.portal.SystemException;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.lar.PortletDataContext;
import com.liferay.portal.lar.PortletDataException;
import com.liferay.portal.lar.PortletDataHandler;
import com.liferay.portal.lar.PortletDataHandlerBoolean;
import com.liferay.portal.lar.PortletDataHandlerControl;
import com.liferay.portal.lar.PortletDataHandlerKeys;
import com.liferay.portal.util.PortletKeys;
import com.liferay.portlet.bookmarks.NoSuchEntryException;
import com.liferay.portlet.bookmarks.NoSuchFolderException;
import com.liferay.portlet.bookmarks.model.BookmarksEntry;
import com.liferay.portlet.bookmarks.model.BookmarksFolder;
import com.liferay.portlet.bookmarks.model.impl.BookmarksFolderImpl;
import com.liferay.portlet.bookmarks.service.BookmarksEntryLocalServiceUtil;
import com.liferay.portlet.bookmarks.service.BookmarksFolderLocalServiceUtil;
import com.liferay.portlet.bookmarks.service.persistence.BookmarksEntryFinderUtil;
import com.liferay.portlet.bookmarks.service.persistence.BookmarksEntryUtil;
import com.liferay.portlet.bookmarks.service.persistence.BookmarksFolderUtil;
import com.liferay.util.MapUtil;

import java.util.List;
import java.util.Map;

import javax.portlet.PortletPreferences;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <a href="BookmarksPortletDataHandlerImpl.java.html"><b><i>View Source</i></b>
 * </a>
 *
 * @author Jorge Ferrer
 * @author Bruno Farache
 * @author Raymond Augé
 *
 */
public class BookmarksPortletDataHandlerImpl implements PortletDataHandler {

	public PortletPreferences deleteData(
			PortletDataContext context, String portletId,
			PortletPreferences prefs)
		throws PortletDataException {

		try {
			if (!context.addPrimaryKey(
					BookmarksPortletDataHandlerImpl.class, "deleteData")) {

				BookmarksFolderLocalServiceUtil.deleteFolders(
					context.getGroupId());
			}

			return null;
		}
		catch (Exception e) {
			throw new PortletDataException(e);
		}
	}

	public String exportData(
			PortletDataContext context, String portletId,
			PortletPreferences prefs)
		throws PortletDataException {

		try {
			Document doc = SAXReaderUtil.createDocument();

			Element root = doc.addElement("bookmarks-data");

			root.addAttribute("group-id", String.valueOf(context.getGroupId()));

			Element foldersEl = root.addElement("folders");
			Element entriesEl = root.addElement("entries");

			List<BookmarksFolder> folders = BookmarksFolderUtil.findByGroupId(
				context.getGroupId());

			for (BookmarksFolder folder : folders) {
				exportFolder(context, foldersEl, entriesEl, folder);
			}

			return doc.formattedString();
		}
		catch (Exception e) {
			throw new PortletDataException(e);
		}
	}

	public PortletDataHandlerControl[] getExportControls() {
		return new PortletDataHandlerControl[] {_foldersAndEntries, _tags};
	}

	public PortletDataHandlerControl[] getImportControls() {
		return new PortletDataHandlerControl[] {_foldersAndEntries, _tags};
	}

	public PortletPreferences importData(
			PortletDataContext context, String portletId,
			PortletPreferences prefs, String data)
		throws PortletDataException {

		try {
			Document doc = SAXReaderUtil.read(data);

			Element root = doc.getRootElement();

			List<Element> folderEls = root.element("folders").elements(
				"folder");

			Map<Long, Long> folderPKs =
				(Map<Long, Long>)context.getNewPrimaryKeysMap(
					BookmarksFolder.class);

			for (Element folderEl : folderEls) {
				String path = folderEl.attributeValue("path");

				if (!context.isPathNotProcessed(path)) {
					continue;
				}

				BookmarksFolder folder =
					(BookmarksFolder)context.getZipEntryAsObject(path);

				importFolder(context, folderPKs, folder);
			}

			List<Element> entryEls = root.element("entries").elements("entry");

			for (Element entryEl : entryEls) {
				String path = entryEl.attributeValue("path");

				if (!context.isPathNotProcessed(path)) {
					continue;
				}

				BookmarksEntry entry =
					(BookmarksEntry)context.getZipEntryAsObject(path);

				importEntry(context, folderPKs, entry);
			}

			return null;
		}
		catch (Exception e) {
			throw new PortletDataException(e);
		}
	}

	public boolean isPublishToLiveByDefault() {
		return false;
	}

	protected void exportFolder(
			PortletDataContext context, Element foldersEl, Element entriesEl,
			BookmarksFolder folder)
		throws PortalException, SystemException {

		if (context.isWithinDateRange(folder.getModifiedDate())) {
			String path = getFolderPath(context, folder);

			if (context.isPathNotProcessed(path)) {
				Element folderEl = foldersEl.addElement("folder");

				folderEl.addAttribute("path", path);

				folder.setUserUuid(folder.getUserUuid());

				context.addZipEntry(path, folder);
			}

			exportParentFolder(context, foldersEl, folder.getParentFolderId());
		}

		List<BookmarksEntry> entries = BookmarksEntryUtil.findByFolderId(
			folder.getFolderId());

		for (BookmarksEntry entry : entries) {
			exportEntry(context, foldersEl, entriesEl, entry);
		}
	}

	protected void exportEntry(
			PortletDataContext context, Element foldersEl, Element entriesEl,
			BookmarksEntry entry)
		throws PortalException, SystemException {

		if (!context.isWithinDateRange(entry.getModifiedDate())) {
			return;
		}

		String path = getEntryPath(context, entry);

		if (context.isPathNotProcessed(path)) {
			Element entryEl = entriesEl.addElement("entry");

			entryEl.addAttribute("path", path);

			if (context.getBooleanParameter(_NAMESPACE, "tags")) {
				context.addTagsEntries(
					BookmarksEntry.class, entry.getEntryId());
			}

			entry.setUserUuid(entry.getUserUuid());

			context.addZipEntry(path, entry);
		}

		exportParentFolder(context, foldersEl, entry.getFolderId());
	}

	protected void exportParentFolder(
			PortletDataContext context, Element foldersEl, long folderId)
		throws PortalException, SystemException {

		if (folderId == BookmarksFolderImpl.DEFAULT_PARENT_FOLDER_ID) {
			return;
		}

		BookmarksFolder folder = BookmarksFolderUtil.findByPrimaryKey(folderId);

		String path = getFolderPath(context, folder);

		if (context.isPathNotProcessed(path)) {
			Element folderEl = foldersEl.addElement("folder");

			folderEl.addAttribute("path", path);

			folder.setUserUuid(folder.getUserUuid());

			context.addZipEntry(path, folder);
		}

		exportParentFolder(context, foldersEl, folder.getParentFolderId());
	}

	protected String getEntryPath(
		PortletDataContext context, BookmarksEntry entry) {

		StringBuilder sb = new StringBuilder();

		sb.append(context.getPortletPath(PortletKeys.BOOKMARKS));
		sb.append("/entries/");
		sb.append(entry.getEntryId());
		sb.append(".xml");

		return sb.toString();
	}

	protected String getFolderPath(
		PortletDataContext context, BookmarksFolder folder) {

		StringBuilder sb = new StringBuilder();

		sb.append(context.getPortletPath(PortletKeys.BOOKMARKS));
		sb.append("/folders/");
		sb.append(folder.getFolderId());
		sb.append(".xml");

		return sb.toString();
	}

	protected void importEntry(
			PortletDataContext context, Map<Long, Long> folderPKs,
			BookmarksEntry entry)
		throws Exception {

		long userId = context.getUserId(entry.getUserUuid());
		long folderId = MapUtil.getLong(
			folderPKs, entry.getFolderId(), entry.getFolderId());

		String[] tagsEntries = null;

		if (context.getBooleanParameter(_NAMESPACE, "tags")) {
			tagsEntries = context.getTagsEntries(
				BookmarksEntry.class, entry.getEntryId());
		}

		boolean addCommunityPermissions = true;
		boolean addGuestPermissions = true;

		BookmarksEntry existingEntry = null;

		try {
			BookmarksFolderUtil.findByPrimaryKey(folderId);

			if (context.getDataStrategy().equals(
					PortletDataHandlerKeys.DATA_STRATEGY_MIRROR)) {

				try {
					existingEntry = BookmarksEntryFinderUtil.findByUuid_G(
						entry.getUuid(), context.getGroupId());

					BookmarksEntryLocalServiceUtil.updateEntry(
						userId, existingEntry.getEntryId(), folderId,
						entry.getName(), entry.getUrl(), entry.getComments(),
						tagsEntries);
				}
				catch (NoSuchEntryException nsee) {
					BookmarksEntryLocalServiceUtil.addEntry(
						entry.getUuid(), userId, folderId, entry.getName(),
						entry.getUrl(), entry.getComments(), tagsEntries,
						addCommunityPermissions, addGuestPermissions);
				}
			}
			else {
				BookmarksEntryLocalServiceUtil.addEntry(
					userId, folderId, entry.getName(), entry.getUrl(),
					entry.getComments(), tagsEntries, addCommunityPermissions,
					addGuestPermissions);
			}
		}
		catch (NoSuchFolderException nsfe) {
			_log.error(
				"Could not find the parent folder for entry " +
					entry.getEntryId());
		}
	}

	protected void importFolder(
			PortletDataContext context, Map<Long, Long> folderPKs,
			BookmarksFolder folder)
		throws Exception {

		long userId = context.getUserId(folder.getUserUuid());
		long plid = context.getPlid();
		long parentFolderId = MapUtil.getLong(
			folderPKs, folder.getParentFolderId(), folder.getParentFolderId());

		boolean addCommunityPermissions = true;
		boolean addGuestPermissions = true;

		BookmarksFolder existingFolder = null;

		try {
			if (parentFolderId !=
					BookmarksFolderImpl.DEFAULT_PARENT_FOLDER_ID) {

				BookmarksFolderUtil.findByPrimaryKey(parentFolderId);
			}

			if (context.getDataStrategy().equals(
					PortletDataHandlerKeys.DATA_STRATEGY_MIRROR)) {
				existingFolder = BookmarksFolderUtil.fetchByUUID_G(
					folder.getUuid(), context.getGroupId());

				if (existingFolder == null) {
					existingFolder = BookmarksFolderLocalServiceUtil.addFolder(
						folder.getUuid(), userId, plid, parentFolderId,
						folder.getName(), folder.getDescription(),
						addCommunityPermissions, addGuestPermissions);
				}
				else {
					existingFolder =
						BookmarksFolderLocalServiceUtil.updateFolder(
							existingFolder.getFolderId(), parentFolderId,
							folder.getName(), folder.getDescription(), false);
				}
			}
			else {
				existingFolder = BookmarksFolderLocalServiceUtil.addFolder(
					userId, plid, parentFolderId, folder.getName(),
					folder.getDescription(), addCommunityPermissions,
					addGuestPermissions);
			}

			folderPKs.put(folder.getFolderId(), existingFolder.getFolderId());
		}
		catch (NoSuchFolderException nsfe) {
			_log.error(
				"Could not find the parent folder for folder " +
					folder.getFolderId());
		}
	}

	private static final String _NAMESPACE = "bookmarks";

	private static final PortletDataHandlerBoolean _foldersAndEntries =
		new PortletDataHandlerBoolean(
			_NAMESPACE, "folders-and-entries", true, true);

	private static final PortletDataHandlerBoolean _tags =
		new PortletDataHandlerBoolean(_NAMESPACE, "tags");

	private static Log _log =
		LogFactory.getLog(BookmarksPortletDataHandlerImpl.class);

}