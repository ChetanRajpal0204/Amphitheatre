/*
 * Copyright (C) 2014 Jerrell Mardis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jerrellmardis.amphitheatre.activity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.jerrellmardis.amphitheatre.R;
import com.jerrellmardis.amphitheatre.util.Utils;

public class BrowseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("amp:BrowseActivity", "Loading app");
        Toast.makeText(this, "Loading Videos...", Toast.LENGTH_SHORT).show();
        /*View v = new View(this);
        v.setBackgroundColor(getResources().getColor(R.color.background_material_dark));
        setContentView(v);*/
        setContentView(R.layout.activity_browse);
        Log.d("amp:BrowseActivity", "Created activity, schedule library update");
        Utils.scheduleLibraryUpdateService(this);
        Utils.scheduleRecommendations(this);
    }
}