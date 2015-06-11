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
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.jerrellmardis.amphitheatre.R;

public class DetailsActivity extends Activity {
    private static String TAG = "amp:DetailsActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
    }

    @Override
    public boolean onSearchRequested() {
        Log.d(TAG, "Search requested");
        startActivity(new Intent(this, SearchActivity.class));
        return true;
    }
}