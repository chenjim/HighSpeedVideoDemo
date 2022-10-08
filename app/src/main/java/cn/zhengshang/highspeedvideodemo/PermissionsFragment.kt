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
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

/**
 * In this [Fragment] we let users pick a camera
 */
class PermissionsFragment : Fragment() {
    companion object {
        const val TAG = "PermissionsFragment"
    }

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


    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!hasPermissionsGranted(CaptureHighSpeedVideoModeFragment.VIDEO_PERMISSIONS)) {
            requestVideoPermissions()
        } else {
            goSelectFragment()
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private fun shouldShowRequestPermissionRationale(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true
            }
        }
        return false
    }

    /**
     * Requests permissions needed for recording video.
     */
    private fun requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(CaptureHighSpeedVideoModeFragment.VIDEO_PERMISSIONS)) {
            ConfirmationDialog.newInstance(R.string.permission_request)
                .setOkListener { dialog, which ->
                    requestPermissions(
                        CaptureHighSpeedVideoModeFragment.VIDEO_PERMISSIONS,
                        CaptureHighSpeedVideoModeFragment.REQUEST_VIDEO_PERMISSIONS
                    )
                }
                .setCancelListener { dialog, which -> activity!!.finish() }
                .show(fragmentManager, "TAG")
        } else {
            requestPermissions(CaptureHighSpeedVideoModeFragment.VIDEO_PERMISSIONS, CaptureHighSpeedVideoModeFragment.REQUEST_VIDEO_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionsResult")
        if (requestCode == CaptureHighSpeedVideoModeFragment.REQUEST_VIDEO_PERMISSIONS) {
            var allGrant = true
            if (grantResults.size == CaptureHighSpeedVideoModeFragment.VIDEO_PERMISSIONS.size) {
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGrant = false
                        break
                    }
                }
            } else {
                allGrant = false
            }
            if (allGrant) {
                goSelectFragment()
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                    .show(childFragmentManager, CaptureHighSpeedVideoModeFragment.FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun goSelectFragment() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.container, SelectorFragment())
            .commit()
    }

    private fun hasPermissionsGranted(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(activity!!, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

}
