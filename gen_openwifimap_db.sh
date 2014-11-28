#! /bin/bash
#
#   Quick and dirty script to build and install a new
#   wifi APs location database on a phone for microg/nogapps
#   OpenWlanMapNlpBackend.
#


#
#   Get latest wifi AP locations from OpenWLANMap.org
#
echo 'Getting wifi AP locations from OpenWLANMap.org'
if [ -e db.tar.bz2 ] ; then
    rm db.tar.bz2
fi
if [ -e db.csv ] ; then
    mv -f db.csv db.csv.bak
fi
wget "http://openwlanmap.org/db.tar.bz2"
tar --strip-components=1 -xjf db.tar.bz2 db/db.csv


echo 'Building database file'
if [ -e openwifimap.db ] ; then
    mv -f openwifimap.db openwifimap.db.bak
fi

### TODO: Filter all entries with lat or long = 0
###       Those are the _nomap entries

sqlite3 openwifimap.db <<!
CREATE TABLE APs(bssid STRING, latitude REAL, longitude REAL);
.mode csv
.separator "\t"
.import db.csv APs
CREATE INDEX _idx1 ON APs (bssid);
VACUUM;
.quit
!

#
#   Push the new database to the phone. 
#
echo 'Pushing database to phone'
adb push openwifimap.db /sdcard/.nogapps/openwifimap.db
