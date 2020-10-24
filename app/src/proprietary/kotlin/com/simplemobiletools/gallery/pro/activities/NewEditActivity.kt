package com.simplemobiletools.gallery.pro.activities

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_STORAGE
import com.simplemobiletools.commons.helpers.REAL_FILE_PATH
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isNougatPlus
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.dialogs.SaveAsDialog
import com.simplemobiletools.gallery.pro.extensions.config
import com.simplemobiletools.gallery.pro.extensions.fixDateTaken
import ly.img.android.pesdk.PhotoEditorSettingsList
import ly.img.android.pesdk.assets.filter.basic.FilterPackBasic
import ly.img.android.pesdk.assets.font.basic.FontPackBasic
import ly.img.android.pesdk.backend.model.config.CropAspectAsset
import ly.img.android.pesdk.backend.model.constant.ImageExportFormat
import ly.img.android.pesdk.backend.model.constant.OutputMode
import ly.img.android.pesdk.backend.model.state.BrushSettings
import ly.img.android.pesdk.backend.model.state.LoadSettings
import ly.img.android.pesdk.backend.model.state.PhotoEditorSaveSettings
import ly.img.android.pesdk.backend.model.state.manager.SettingsList
import ly.img.android.pesdk.ui.activity.PhotoEditorBuilder
import ly.img.android.pesdk.ui.model.state.*
import ly.img.android.pesdk.ui.panels.item.CropAspectItem
import ly.img.android.pesdk.ui.panels.item.ToggleAspectItem
import ly.img.android.pesdk.ui.panels.item.ToolItem
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class NewEditActivity : SimpleActivity() {
    private val PESDK_EDIT_IMAGE = 1
    private val SETTINGS_LIST = "SETTINGS_LIST"
    private val SOURCE_URI = "SOURCE_URI"
    private val RESULT_URI = "RESULT_URI"
    private var sourceFileLastModified = 0L
    private var oldExif: ExifInterface? = null

    private lateinit var uri: Uri
    private lateinit var saveUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_edit)

        if (checkAppSideloading()) {
            return
        }

        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                initEditActivity()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun initEditActivity() {
        if (intent.data == null) {
            toast(R.string.invalid_image_path)
            finish()
            return
        }

        uri = intent.data!!
        if (uri.scheme != "file" && uri.scheme != "content") {
            toast(R.string.unknown_file_location)
            finish()
            return
        }

        if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
            val realPath = intent.extras!!.getString(REAL_FILE_PATH)
            uri = when {
                isPathOnOTG(realPath!!) -> uri
                realPath.startsWith("file:/") -> Uri.parse(realPath)
                else -> Uri.fromFile(File(realPath))
            }
        } else {
            (getRealPathFromURI(uri))?.apply {
                uri = Uri.fromFile(File(this))
            }
        }

        saveUri = when {
            intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true -> intent.extras!!.get(MediaStore.EXTRA_OUTPUT) as Uri
            else -> uri
        }

        openEditor(uri)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == PESDK_EDIT_IMAGE) {
            val extras = resultData?.extras
            val resultPath = extras?.get(RESULT_URI)?.toString() ?: ""
            val sourcePath = Uri.decode(extras?.get(SOURCE_URI)?.toString() ?: "")
            val settings = extras?.getParcelable<SettingsList>(SETTINGS_LIST)
            if (settings != null) {
                val brush = settings.getSettingsModel(BrushSettings::class.java)
                config.editorBrushColor = brush.brushColor
                config.editorBrushHardness = brush.brushHardness
                config.editorBrushSize = brush.brushSize
            }

            if (resultCode != Activity.RESULT_OK || resultPath.isEmpty()) {
                toast(R.string.image_editing_cancelled)
                finish()
            } else {
                val source = if (sourcePath.isEmpty() || sourcePath.startsWith("content")) {
                    internalStoragePath
                } else {
                    sourcePath.substringAfter("file://")
                }

                SaveAsDialog(this, source, true, cancelCallback = {
                    toast(R.string.image_editing_failed)
                    finish()
                }, callback = {
                    val destinationFilePath = it
                    handleSAFDialog(destinationFilePath) {
                        if (it) {
                            ensureBackgroundThread {
                                storeOldExif(source)
                                sourceFileLastModified = File(source).lastModified()

                                var inputStream: InputStream? = null
                                var outputStream: OutputStream? = null
                                try {
                                    inputStream = contentResolver.openInputStream(Uri.parse(resultPath))
                                    outputStream = getFileOutputStreamSync(destinationFilePath, destinationFilePath.getMimeType())
                                    inputStream!!.copyTo(outputStream!!)
                                    outputStream.flush()
                                    inputStream.close()
                                    outputStream.close()

                                    try {
                                        if (isNougatPlus()) {
                                            val newExif = ExifInterface(destinationFilePath)
                                            oldExif?.copyTo(newExif, false)
                                        }
                                    } catch (ignored: Exception) {
                                    }

                                    if (config.keepLastModified) {
                                        // add 1 s to the last modified time to properly update the thumbnail
                                        updateLastModified(destinationFilePath, sourceFileLastModified + 1000)
                                    }

                                    val paths = arrayListOf(destinationFilePath)
                                    rescanPaths(arrayListOf(destinationFilePath)) {
                                        fixDateTaken(paths, false)
                                    }

                                    setResult(Activity.RESULT_OK, intent)
                                    toast(R.string.file_edited_successfully)
                                    finish()
                                } catch (e: Exception) {
                                    showErrorToast(e)
                                } finally {
                                    inputStream?.close()
                                    outputStream?.close()
                                }
                            }
                        } else {
                            toast(R.string.image_editing_failed)
                            finish()
                        }
                    }
                })
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun storeOldExif(sourcePath: String) {
        var inputStream: InputStream? = null
        try {
            if (isNougatPlus()) {
                inputStream = contentResolver.openInputStream(Uri.fromFile(File(sourcePath)))
                oldExif = ExifInterface(inputStream!!)
            }
        } catch (ignored: Exception) {
        } finally {
            inputStream?.close()
        }
    }

    private fun openEditor(inputImage: Uri) {
        val settingsList = createPesdkSettingsList()

        settingsList.configure<LoadSettings> {
            it.source = inputImage
        }

        settingsList[LoadSettings::class].source = inputImage

        PhotoEditorBuilder(this)
            .setSettingsList(settingsList)
            .startActivityForResult(this, PESDK_EDIT_IMAGE)
    }

    private fun createPesdkSettingsList(): PhotoEditorSettingsList {
        val settingsList = PhotoEditorSettingsList().apply {
            configure<UiConfigFilter> {
                it.setFilterList(FilterPackBasic.getFilterPack())
            }

            configure<UiConfigText> {
                it.setFontList(FontPackBasic.getFontPack())
            }

            config.getAssetMap(CropAspectAsset::class.java).apply {
                add(CropAspectAsset("my_crop_1_2", 1, 2, false))
                add(CropAspectAsset("my_crop_2_1", 2, 1, false))
                add(CropAspectAsset("my_crop_19_9", 19, 9, false))
                add(CropAspectAsset("my_crop_9_19", 9, 19, false))
                add(CropAspectAsset("my_crop_5_4", 5, 4, false))
                add(CropAspectAsset("my_crop_4_5", 4, 5, false))
                add(CropAspectAsset("my_crop_37_18", 37, 18, false))
                add(CropAspectAsset("my_crop_18_37", 18, 37, false))
                add(CropAspectAsset("my_crop_16_10", 16, 10, false))
                add(CropAspectAsset("my_crop_10_16", 10, 16, false))
            }

            getSettingsModel(UiConfigAspect::class.java).aspectList.apply {
                add(ToggleAspectItem(CropAspectItem("my_crop_2_1"), CropAspectItem("my_crop_1_2")))
                add(ToggleAspectItem(CropAspectItem("my_crop_19_9"), CropAspectItem("my_crop_9_19")))
                add(ToggleAspectItem(CropAspectItem("my_crop_5_4"), CropAspectItem("my_crop_4_5")))
                add(ToggleAspectItem(CropAspectItem("my_crop_37_18"), CropAspectItem("my_crop_18_37")))
                add(ToggleAspectItem(CropAspectItem("my_crop_16_10"), CropAspectItem("my_crop_10_16")))
            }

            getSettingsModel(BrushSettings::class.java).apply {
                brushColor = applicationContext.config.editorBrushColor
                brushHardness = applicationContext.config.editorBrushHardness
                brushSize = applicationContext.config.editorBrushSize
            }

            // do not use Text Design, it takes up too much space
            val tools = getSettingsModel(UiConfigMainMenu::class.java).toolList
            val newTools = tools.filterNot {
                it.name!!.isEmpty()
            }.toMutableList() as ArrayList<ToolItem>

            // move Focus at the end, as it is the least used
            // on some devices it is not obvious that the toolbar can be scrolled horizontally, so move the best ones at the beginning to make them visible
            val focus = newTools.firstOrNull { it.name == getString(R.string.pesdk_focus_title_name) }
            if (focus != null) {
                newTools.remove(focus)
                newTools.add(focus)
            }

            getSettingsModel(UiConfigMainMenu::class.java).setToolList(newTools)

            getSettingsModel(UiConfigTheme::class.java).theme = R.style.Imgly_Theme_NoFullscreen

            configure<PhotoEditorSaveSettings> {
                it.setExportFormat(ImageExportFormat.AUTO)
                it.setOutputToTemp()
                it.outputMode = OutputMode.EXPORT_IF_NECESSARY
            }
        }

        return settingsList
    }
}
