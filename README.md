OpenWlanMapNlpBackend
=====================
[UnifiedNlp](https://github.com/microg/android_packages_apps_UnifiedNlp) backend that uses [OpenWlanMap](http://www.openwlanmap.org/) to resolve user location.

Location calculation is done either online or offline. This can be switched in the settings.
Online calculation of course requires internet connection.
Offline calculation won't use any data, but look up the wifi access points in a database on your sd-card only. 
To generate the database a shell script (gen_openwifimap_db.sh) is included.

To contribute to the OpenWlanMap database you can use the available Android app or upload your "wardriving" data manually [here](https://openwlanmap.org/upload.php?lang=). Don't forget to enable the "Publish own data" in the Android apps settings!

Building
--------
Currently does not ship any build system or build system specific files. Use your favourite one.

Building requires Android SDK with API 18 or higher.

Used libraries
--------------
-	[UnifiedNlpApi](https://github.com/microg/android_packages_apps_UnifiedNlp)
-	[libwlocate](http://sourceforge.net/projects/libwlocate/) (included)


License
-------
libwlocate is GPLv3, so is OpenWlanMapNlpBackend.

    Copyright (C) 2014 μg Project Team

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
