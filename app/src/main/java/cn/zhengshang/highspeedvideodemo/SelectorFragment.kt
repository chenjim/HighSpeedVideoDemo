/*
 * Copyright (C) 2021 The Android Open Source Project
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

package cn.zhengshang.highspeedvideodemo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * In this [Fragment] we let users pick a camera
 */
class SelectorFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = RecyclerView(requireContext())

    /** Converts a lens orientation enum into a human-readable string */
    private fun lensOrientationString(value: Int) = when (value) {
        CameraCharacteristics.LENS_FACING_BACK -> "Back"
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown"
    }

    /** Lists all extension-capable cameras*/
    @SuppressLint("InlinedApi")
    private fun enumerateExtensionCameras(cameraManager: CameraManager): List<CameraInfo> {
        val availableCameras: MutableList<CameraInfo> = mutableListOf()

        cameraManager.cameraIdList.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val orientation = lensOrientationString(
                characteristics.get(CameraCharacteristics.LENS_FACING)!!
            )
            // Return cameras that declare to be backward compatible
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            map?.let {
                val highSpeedSizes = map.highSpeedVideoSizes
                highSpeedSizes.sortBy { it.width }
                for (size: android.util.Size in highSpeedSizes) {
                    val ranges = map.getHighSpeedVideoFpsRangesFor(size)
                    for (fps in ranges) {
                        if (fps.lower == fps.upper) {
                            val fSize = Size(size.width, size.height, fps.lower)
                            val name = "$orientation ($id) ${size.width}x${size.height} ${fps.lower}"
                            availableCameras.add(CameraInfo(name, id, fSize))
                        }
                    }
                }
            }

        }

        return availableCameras
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view as RecyclerView
        view.apply {
            layoutManager = LinearLayoutManager(requireContext())

            val cameraManager =
                requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val cameraList = enumerateExtensionCameras(cameraManager)

            if (cameraList.isEmpty()) {
                Toast.makeText(requireContext(), "不支持", Toast.LENGTH_LONG).show()
                return
            }

            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(cameraList, itemLayoutId = layoutId) { view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).apply {
                    text = item.name
                    setTextColor(Color.WHITE)
                }
                view.setOnClickListener {
                    CaptureHighSpeedVideoModeFragment.config = item
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.container, CaptureHighSpeedVideoModeFragment())
                        .commit()
                }
            }
            Toast.makeText(requireContext(), "请选择", Toast.LENGTH_SHORT).show()
            Logger.d(cameraList.size)
        }
    }
}
