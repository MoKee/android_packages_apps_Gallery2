/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.TimeIntervalPopup;
import com.android.gallery3d.R;

import java.util.Locale;

public class VideoMenu extends PieController
        implements ListPrefSettingPopup.Listener,
        TimeIntervalPopup.Listener {

    private static String TAG = "CAM_VideoMenu";

    private VideoUI mUI;
    private AbstractSettingPopup mPopup;
    private CameraActivity mActivity;

    public VideoMenu(CameraActivity activity, VideoUI ui, PieRenderer pie) {
        super(activity, pie);
        mUI = ui;
        mActivity = activity;
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup = null;
        PieItem item = null;
        final Resources res = mActivity.getResources();
        Locale locale = res.getConfiguration().locale;
        // smart capture
        if (group.findPreference(CameraSettings.KEY_SMART_CAPTURE_VIDEO) != null) {
            item = makeSwitchItem(CameraSettings.KEY_SMART_CAPTURE_VIDEO, true);
            mRenderer.addItem(item);
        }
        // more options
        PieItem more = makeItem(R.drawable.ic_more_options);
        more.setLabel(res.getString(R.string.camera_menu_more_label));
        mRenderer.addItem(more);
        // camera switcher
        if (group.findPreference(CameraSettings.KEY_CAMERA_ID) != null) {
            item = makeItem(R.drawable.ic_switch_back);
            IconListPreference lpref = (IconListPreference) group.findPreference(
                    CameraSettings.KEY_CAMERA_ID);
            item.setLabel(lpref.getLabel());
            item.setImageResource(mActivity,
                    ((IconListPreference) lpref).getIconIds()
                    [lpref.findIndexOfValue(lpref.getValue())]);

            final PieItem fitem = item;
            item.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref =
                            mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
                    if (pref != null) {
                        int index = pref.findIndexOfValue(pref.getValue());
                        CharSequence[] values = pref.getEntryValues();
                        index = (index + 1) % values.length;
                        int newCameraId = Integer.parseInt((String) values[index]);
                        fitem.setImageResource(mActivity,
                                ((IconListPreference) pref).getIconIds()[index]);
                        fitem.setLabel(pref.getLabel());
                        mListener.onCameraPickerClicked(newCameraId);
                    }
                }
            });
            mRenderer.addItem(item);
        }
        // flash
        if (group.findPreference(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE) != null) {
            item = makeItem(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE);
            mRenderer.addItem(item);
        }
        // time laps frame interval
        if (group.findPreference(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL) != null) {
            item = makeItem(R.drawable.ic_timelapse_none);
            final IconListPreference timeLapsPref = (IconListPreference)
                group.findPreference(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
            item.setLabel(res.getString(
                R.string.pref_video_time_lapse_frame_interval_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    LayoutInflater inflater =  mActivity.getLayoutInflater();
                    TimeIntervalPopup popup = (TimeIntervalPopup) inflater.inflate(
                    R.layout.time_interval_popup, null, false);
                    popup.initialize(timeLapsPref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mUI.showPopup(mPopup);
                }
            });
            more.addItem(item);
        }
        // white balance
        if (group.findPreference(CameraSettings.KEY_WHITE_BALANCE) != null) {
            item = makeItem(CameraSettings.KEY_WHITE_BALANCE);
            more.addItem(item);
        }
        // color effects
        if (group.findPreference(CameraSettings.KEY_VIDEO_COLOR_EFFECT) != null) {
            item = makeItem(R.drawable.ic_color_effect);
            final ListPreference effectPref = group.findPreference(CameraSettings.KEY_VIDEO_COLOR_EFFECT);
            item.setLabel(res.getString(R.string.pref_coloreffect_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    ListPrefSettingPopup popup = (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(effectPref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mUI.showPopup(mPopup);
                }
            });
            more.addItem(item);
        }
        // video effects
        if (group.findPreference(CameraSettings.KEY_VIDEO_EFFECT) != null) {
            item = makeItem(R.drawable.ic_effects_holo_light);
            final ListPreference effectPref = group.findPreference(CameraSettings.KEY_VIDEO_EFFECT);
            item.setLabel(res.getString(R.string.pref_video_effect_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    ListPrefSettingPopup popup = (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(effectPref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mUI.showPopup(mPopup);
                }
            });
            more.addItem(item);
        }
        // settings
        PieItem settings = makeItem(R.drawable.ic_settings_holo_light);
        settings.setLabel(res.getString(R.string.camera_menu_settings_label));
        more.addItem(settings);
        // location
        if (group.findPreference(CameraSettings.KEY_RECORD_LOCATION) != null) {
            item = makeSwitchItem(CameraSettings.KEY_RECORD_LOCATION, true);
            settings.addItem(item);
            if (mActivity.isSecureCamera()) {
                // Prevent location preference from getting changed in secure camera mode
                item.setEnabled(false);
            }
        }
        // video quality
        if (group.findPreference(CameraSettings.KEY_VIDEO_QUALITY) != null) {
            item = makeItem(R.drawable.ic_imagesize);
            final ListPreference qualityPref = group.findPreference(CameraSettings.KEY_VIDEO_QUALITY);
            item.setLabel(res.getString(R.string.pref_video_quality_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    ListPrefSettingPopup popup = (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(qualityPref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mUI.showPopup(mPopup);
                }
            });
            settings.addItem(item);
        }
        // jpeg quality
        if (group.findPreference(CameraSettings.KEY_VIDEO_JPEG) != null
            && !Util.disableTouchSnapshot()) {
            item = makeItem(R.drawable.ic_jpeg);
            final ListPreference effectPref = group.findPreference(CameraSettings.KEY_VIDEO_JPEG);
            item.setLabel(res.getString(R.string.pref_jpeg_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    ListPrefSettingPopup popup = (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(effectPref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mUI.showPopup(mPopup);
                }
            });
            settings.addItem(item);
        }
        // true view
        if (group.findPreference(CameraSettings.KEY_TRUE_VIEW) != null) {
            item = makeSwitchItem(CameraSettings.KEY_TRUE_VIEW, true);
            item.setLabel(res.getString(R.string.pref_true_view_label).toUpperCase(locale));
            settings.addItem(item);
        }
        // Storage location
        if (group.findPreference(CameraSettings.KEY_STORAGE) != null) {
            item = makeItem(R.drawable.stat_notify_sdcard);
            final ListPreference storagePref = group.findPreference(CameraSettings.KEY_STORAGE);
            item.setLabel(res.getString(R.string.pref_camera_storage_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    LayoutInflater inflater =  mActivity.getLayoutInflater();
                    ListPrefSettingPopup popup = (ListPrefSettingPopup) inflater.inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(storagePref);
                    popup.setSettingChangedListener(VideoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mUI.showPopup(mPopup);
                }
            });
            settings.addItem(item);
        }
    }

    @Override
    // Hit when an item in a popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup != null) {
            mUI.dismissPopup();
        }
        onSettingChanged(pref);
    }

    public void popupDismissed() {
        // the popup gets dismissed
        if (mPopup != null) {
            mPopup = null;
        }
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    private void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

}
