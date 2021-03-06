/*
 * Copyright (C) 2013 - 2015. Christopher Boyd
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package us.cboyd.android.dicom.tag

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.SpecificCharacterSet
import org.dcm4che3.data.VR
import us.cboyd.android.dicom.DcmRes
import us.cboyd.android.dicom.DcmUid
import java.text.SimpleDateFormat

/**
 * Created by Christopher on 6/1/2015.
 */
class TagRecyclerAdapter(context: Context, private val mResource: Int,
                         private val mAttributes: Attributes, arrayId: Int,
                         private var mDebugMode: Boolean) : RecyclerView.Adapter<TagViewHolder>() {
    private val mRes: Resources
    private val mTags: IntArray

    init {
        mRes = context.resources
        mTags = mRes.getIntArray(arrayId)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        // create a new view
        val v = LayoutInflater.from(parent.context)
                .inflate(mResource, parent, false)
        // set the view's size, margins, paddings and layout parameters
        //        ...
        return TagViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        var temp = String.format("%08X", mTags[position])
        holder.tagLeft.text = "(${temp.substring(0, 4)},\n ${temp.substring(4, 8)})"

        temp = DcmRes.getTag(mTags[position], mRes)
        var temp2 = temp.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        holder.text2.text = temp2[0]
        val de = mAttributes.getValue(mTags[position])
        val dvr = mAttributes.getVR(mTags[position])
        // Clear existing data from recycled view
        holder.text1.text = ""
        holder.tagRight.text = ""

        // Only display VR/VM in Debug mode
        if (mDebugMode && dvr != null) {
            holder.tagRight.text = "VR: $dvr"// + "\nVM: ${dvr.vmOf(de)}"
        }
        if (de != null) {
            //SpecificCharacterSet for US_ASCII
            val cs = SpecificCharacterSet.ASCII

            val dStr = de.toString()

            // If in Debug mode, just display the string as-is without any special processing.
            if (mDebugMode) {
                holder.text1.text = dStr
                // Otherwise, make the fields easier to read.
                // Start by formatting the Person Names.
            } else if (dvr == VR.PN) {
                // Family Name^Given Name^Middle Name^Prefix^Suffix
                temp2 = dStr.split("\\^".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                // May omit '^' for trailing null component groups.
                // Use a switch-case statement to deal with this.
                when (temp2.size) {
                    // Last, First
                    2 -> temp = "${temp2[0]}, ${temp2[1]}"
                    // Last, First Middle
                    3 -> temp = "${temp2[0]}, ${temp2[1]} ${temp2[2]}"
                    // Last, Prefix First Middle
                    4 -> temp = "${temp2[0]}, ${temp2[3]} ${temp2[1]} ${temp2[2]}"
                    // Last, Prefix First Middle, Suffix
                    5 -> temp = "${temp2[0]}, ${temp2[3]} ${temp2[1]} ${temp2[2]}, ${temp2[4]}"
                    // All other cases, just display the unmodified string.
                    else -> temp = dStr
                }
                holder.text1.text = temp
                // Translate the known UIDs into plain-text.
            } else if (dvr == VR.UI) {
                temp = DcmUid.get(dStr, mRes)
                // Only want the first field containing the plain-text name.
                temp2 = temp.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                holder.text1.text = temp2[0]
                // Format the date according to the current locale.
            } else if (Build.VERSION.SDK_INT >= 18) {
                if (dvr == VR.DA) {
                    val sdf = SimpleDateFormat("yyyyMMdd")
                    try {
                        val vDate = sdf.parse(dStr)
                        val dPat = DateFormat.getBestDateTimePattern(
                                mRes.configuration.locale, "MMMMdyyyy")
                        sdf.applyPattern(dPat)
                        holder.text1.text = sdf.format(vDate)
                    } catch (e: Exception) {
                        // If the date string couldn't be parsed, display the unmodified string.
                        holder.text1.text = dStr
                    }

                    // Format the date & time according to the current locale.
                } else if (dvr == VR.DT) {
                    val sdf = SimpleDateFormat("yyyyMMddHHmmss.SSSSSSZZZ")
                    try {
                        // Note: The DICOM standard allows for 6 fractional seconds,
                        // but Java can only handle 3.
                        //
                        // Therefore, we must limit the string length.
                        // Use concat to re-append the time-zone.
                        val vDate = sdf.parse(
                                dStr.substring(0, 18) + dStr.substring(21, dStr.length))
                        val dPat = DateFormat.getBestDateTimePattern(
                                mRes.configuration.locale, "MMMMdyyyyHHmmssSSSZZZZ")
                        sdf.applyPattern(dPat)
                        holder.text1.text = sdf.format(vDate)
                    } catch (e: Exception) {
                        // If the date string couldn't be parsed, display the unmodified string.
                        holder.text1.text = dStr
                    }

                    // Format the time according to the current locale.
                } else if (dvr == VR.TM) {
                    val sdf = SimpleDateFormat("HHmmss.SSS")
                    try {
                        // Note: The DICOM standard allows for 6 fractional seconds,
                        // but Java can only handle 3.
                        // Therefore, we must limit the string length.
                        val vDate = sdf.parse(dStr.substring(0, 10))
                        val dPat = DateFormat.getBestDateTimePattern(
                                mRes.configuration.locale, "HHmmssSSS")
                        sdf.applyPattern(dPat)
                        holder.text1.text = sdf.format(vDate)
                    } catch (e: Exception) {
                        // If the time string couldn't be parsed, display the unmodified string.
                        holder.text1.text = dStr
                    }

                } else {
                    holder.text1.text = dStr
                }
            } else {
                holder.text1.text = dStr
            }
        }

    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return mTags.size
    }

    fun setDebugMode(debugMode: Boolean) {
        mDebugMode = debugMode
        notifyDataSetChanged()
    }
}
