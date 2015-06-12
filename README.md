Amphitheatre
============

Amphitheatre is an Android TV project aimed to bring you the best of your content in a simple and visually appealing fashion. It connects to your network shares, organizes and serves videos to an Android capable media player.

![Amphitheatre](images/palette_expanding_info_panel.png)

**Features**
* Indexes movie and TV show files on your SMB, CIFS shares, on a USB hard drive, or stored on the device.
* View movie and TV show poster art and details.
* Quickly search through your video collection in the app or globally.
* Recommends users new movies and TV shows to watch.

Dependencies
------------

#### The Movie Database (TMDb)

Amphitheatre uses The Movie Database (TMDb) in order to fetch movie information.

You'll need to sign up as a developer and add your TMDb API Key to your `~/.gradle/gradle.properties`:
```
TMDB_API_KEY=<your api key>
```

You'll also need to sign up for a [TVDB API Key](http://thetvdb.com/wiki/index.php/Programmers_API) and add it to your `~/.gradle/gradle.properties`:
```
TVDB_API_KEY=<your api key>
```

#### Media Player

Amphitheatre does not play the actual video file but serves it to a capable media player application. So you'll need to install a media player as well. MXPlayer and VLC player are great players worth checking out.

#### Video Groups
Pehaps you have a movie that is in a few parts. This is easy to manage. You can create a "title shared" folder that allows you to store a group of videos under the same name. You can select the video you want to play in the details activity.

The format to do so is as follows:
`MOVIE NAME/VIDEO_TS/movie_part1.avi`
`MOVIE NAME/VIDEO_TS/movie_part2.avi`

Note this currently only works for SMB shares.

#### Ignored Folders
When scanning SMB shares, there are a few folder names that will not be checked. If you have a video in this folder that you want to be presented, you should move it to another folder or file an issue to enable it.

Among the few folder names is `Ignore` or any folder that starts with a `.`. If you want to hide files from being scanned, you should put them into one of these folders.

Contributing
------------

All contributions are welcome!

License
-------

    Copyright 2014 Jerrell Mardis, with modifications by Nick Felker

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
