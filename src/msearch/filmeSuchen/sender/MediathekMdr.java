/*
 * MediathekView
 * Copyright (C) 2008 W. Xaver
 * W.Xaver[at]googlemail.com
 * http://zdfmediathk.sourceforge.net/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package msearch.filmeSuchen.sender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import msearch.daten.DatenFilm;
import msearch.filmeSuchen.MSFilmeSuchen;
import msearch.filmeSuchen.MSGetUrl;
import msearch.tool.MSConfig;
import msearch.tool.MSConst;
import msearch.tool.MSLog;
import msearch.tool.MSStringBuilder;

/**
 *
 * @author
 */
public class MediathekMdr extends MediathekReader implements Runnable {

    public final static String SENDERNAME = "MDR";
    private final LinkedList<String> listeTage = new LinkedList<>();
    private final LinkedList<String[]> listeGesucht = new LinkedList<>(); //thema,titel,datum,zeit

    /**
     *
     * @param ssearch
     * @param startPrio
     */
    public MediathekMdr(MSFilmeSuchen ssearch, int startPrio) {
        super(ssearch, SENDERNAME, /* threads */ 2, /* urlWarten */ 500, startPrio);
    }

    /**
     *
     */
    @Override
    public void addToList() {
        final String URL_SENDUNGEN = "http://www.mdr.de/mediathek/fernsehen/a-z/sendungenabisz100.html";
        final String URL_TAGE = "http://www.mdr.de/mediathek/fernsehen/index.html";
        final String MUSTER = "<a href=\"/mediathek/fernsehen/a-z/sendungenabisz100_letter-";
        final String MUSTER_ADD = "http://www.mdr.de/mediathek/fernsehen/a-z/sendungenabisz100_letter-";
        final String MUSTER_TAGE = "<a href=\"/mediathek/fernsehen/sendungverpasst100-multiGroupClosed_boxIndex-";
        final String MUSTER_ADD_TAGE = "http://www.mdr.de/mediathek/fernsehen/sendungverpasst100-multiGroupClosed_boxIndex-";
        MSStringBuilder seite = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        listeThemen.clear();
        listeTage.clear();
        listeGesucht.clear();
        meldungStart();
        seite = getUrlIo.getUri_Utf(SENDERNAME, URL_SENDUNGEN, seite, "");
        int pos = 0;
        int pos1;
        int pos2;
        String url = "";
        while ((pos = seite.indexOf(MUSTER, pos)) != -1) {
            pos += MUSTER.length();
            pos1 = pos;
            pos2 = seite.indexOf("\"", pos);
            if (pos1 != -1 && pos2 != -1) {
                url = seite.substring(pos1, pos2);
            }
            if (url.equals("")) {
                MSLog.fehlerMeldung(889216307, "keine URL");
            } else {
                url = MUSTER_ADD + url;
                listeThemen.addUrl(new String[]{url});
            }
        }
        seite = getUrlIo.getUri_Utf(SENDERNAME, URL_TAGE, seite, "");
        pos = 0;
        url = "";
        while ((pos = seite.indexOf(MUSTER_TAGE, pos)) != -1) {
            pos += MUSTER_TAGE.length();
            pos1 = pos;
            pos2 = seite.indexOf("\"", pos);
            if (pos1 != -1 && pos2 != -1) {
                url = seite.substring(pos1, pos2);
            }
            if (url.equals("")) {
                MSLog.fehlerMeldung(461225808, "keine URL");
            } else {
                url = MUSTER_ADD_TAGE + url;
                if (!istInListe(listeTage, url)) {
                    listeTage.add(url);
                }
            }
        }
        if (MSConfig.getStop()) {
            meldungThreadUndFertig();
        } else if (listeThemen.size() == 0 && listeTage.size() == 0) {
            meldungThreadUndFertig();
        } else {
            meldungAddMax(listeThemen.size() + listeTage.size());
            listeSort(listeThemen, 0);
            for (int t = 0; t < maxThreadLaufen; ++t) {
                //new Thread(new ThemaLaden()).start();
                Thread th = new Thread(new ThemaLaden());
                th.setName(SENDERNAME + t);
                th.start();
            }
        }
    }

    private class ThemaLaden implements Runnable {

        MSGetUrl getUrl = new MSGetUrl(wartenSeiteLaden);
        private MSStringBuilder seite1 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder seite2 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder seite3 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder seite4 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);
        private MSStringBuilder seite5 = new MSStringBuilder(MSConst.STRING_BUFFER_START_BUFFER);

        @Override
        public void run() {
            try {
                meldungAddThread();
                String[] link;
                while (!MSConfig.getStop() && (link = listeThemen.getListeThemen()) != null) {
                    meldungProgress(link[0]);
                    addThema(link[0]);
                }
                String url;
                while (!MSConfig.getStop() && (url = getListeTage()) != null) {
                    meldungProgress(url);
                    addTage(url);
                }
            } catch (Exception ex) {
                MSLog.fehlerMeldung(115896304, ex);
            }
            meldungThreadUndFertig();
        }

        void addTage(String urlSeite) {
            final String MUSTER_START_1 = "<div class=\"teaserImage\">";
            final String MUSTER_START_2 = "<h3>";
            final String MUSTER_THEMA = "title=\"Zu den Inhalten der Sendung\">";
            final String MUSTER_URL = "<a href=\"/mediathek/fernsehen/";
            final String MUSTER_ADD = "http://www.mdr.de/mediathek/fernsehen/";
            int pos = 0, posStop;
            String url;
            String thema;
            try {
                seite1 = getUrl.getUri_Utf(SENDERNAME, urlSeite, seite1, "");
                posStop = seite1.indexOf("title=\"Was ist das?\">Empfehlen</a>");
                while (!MSConfig.getStop() && (pos = seite1.indexOf(MUSTER_START_1, pos)) != -1) {
                    if (posStop > 0 && pos > posStop) {
                        break;
                    }
                    url = "";
                    thema = "";
                    pos += MUSTER_START_1.length();
                    if ((pos = seite1.indexOf(MUSTER_START_2, pos)) == -1) {
                        break;
                    }
                    pos += MUSTER_START_2.length();
                    thema = seite1.extract(MUSTER_THEMA, "<", pos);
                    url = seite1.extract(MUSTER_URL, "\"", pos);
                    if (url.equals("")) {
                        MSLog.fehlerMeldung(392854069, new String[]{"keine URL: " + urlSeite});
                    } else {
                        url = MUSTER_ADD + url;
                        meldung(url);
                        addSendugTage(urlSeite, thema, url);
                    }
                }// while
            } catch (Exception ex) {
                MSLog.fehlerMeldung(556320478, ex);
            }
        }

        private void addSendugTage(String strUrlFeed, String thema, String urlThema) {
            final String MUSTER_ADD = "http://www.mdr.de/mediathek/fernsehen/";
            seite5 = getUrl.getUri_Utf(SENDERNAME, urlThema, seite5, "Thema: " + thema);
            String url = seite5.extract("dataURL:'/mediathek/fernsehen/", "'");
            if (url.equals("")) {
                MSLog.fehlerMeldung(701025498, new String[]{"keine URL: " + urlThema, "Thema: " + thema, "UrlFeed: " + strUrlFeed});
            } else {
                url = MUSTER_ADD + url;
            }
            if (!MSConfig.getStop()) {
                addXml(strUrlFeed, thema, url, urlThema);
            }
        }

        void addThema(String strUrlFeed) {
            final String MUSTER_TITEL = "title=\"Alle verfügbaren Sendungen anzeigen\">";
            final String MUSTER_URL = "<h3><a href=\"/mediathek/fernsehen/a-z/";
            final String MUSTER_ADD = "http://www.mdr.de/mediathek/fernsehen/a-z/";

            int pos = 0;
            int pos2;
            String thema = "";
            String url;
            try {
                seite2 = getUrl.getUri_Utf(SENDERNAME, strUrlFeed, seite2, "");
                while (!MSConfig.getStop() && (pos = seite2.indexOf(MUSTER_URL, pos)) != -1) {
                    pos += MUSTER_URL.length();
                    pos2 = seite2.indexOf("\"", pos);
                    if (pos != -1 && pos2 != -1) {
                        url = seite2.substring(pos, pos2);
                        pos = pos2;
                        if ((pos = seite2.indexOf(MUSTER_TITEL, pos)) != -1) {
                            pos += MUSTER_TITEL.length();
                            pos2 = seite2.indexOf("<", pos);
                            if (pos != -1 && pos2 != -1) {
                                thema = seite2.substring(pos, pos2);
                                pos = pos2;
                            }
                            if (url.equals("")) {
                                MSLog.fehlerMeldung(766250249, "keine URL: " + strUrlFeed);
                            } else {
                                meldung(url);
                                addSendug(strUrlFeed, thema, MUSTER_ADD + url);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                MSLog.fehlerMeldung(316874602, ex);
            }
        }

        private void addSendug(String strUrlFeed, String thema, String urlThema) {
            final String MUSTER_START = "<span class=\"ressortHead\">Sendungen von A bis Z</span>";
            final String MUSTER_XML = "{container:'mediathekStage',dataURL:'/mediathek/fernsehen/a-z";
            final String MUSTER_ADD = "http://www.mdr.de/mediathek/fernsehen/a-z/";
            LinkedList<String> tmpListe = new LinkedList<>();
            seite3 = getUrl.getUri_Utf(SENDERNAME, urlThema, seite3, "Thema: " + thema);
            int pos;
            int pos1;
            int pos2;
            String url = "";
            if ((pos = seite3.indexOf(MUSTER_START)) != -1) {
                while ((pos = seite3.indexOf(MUSTER_XML, pos)) != -1) {
                    pos += MUSTER_XML.length();
                    pos1 = pos;
                    if ((pos2 = seite3.indexOf("'", pos)) != -1) {
                        url = seite3.substring(pos1, pos2);
                    }
                    if (url.equals("")) {
                        MSLog.fehlerMeldung(256987304, new String[]{"keine URL: " + urlThema, "Thema: " + thema, "UrlFeed: " + strUrlFeed});
                    } else {
                        url = MUSTER_ADD + url;
                        if (!tmpListe.contains(url)) {
                            tmpListe.add(url);
                        }
                    }
                }
            }
            Iterator<String> it = tmpListe.iterator();
            while (!MSConfig.getStop() && it.hasNext()) {
                addXml(strUrlFeed, thema, it.next(), urlThema);
            }
        }

        void addXml(String strUrlFeed, String thema, String xmlSite, String filmSite) {
            final String MUSTER_START = "<avDocument>";
            final String MUSTER_ENDE = "</avDocument>";
            final String MUSTER_TITEL = "<title>";
            final String MUSTER_DATUM = "<broadcastStartDate>";
            final String MUSTER_DURATION = "<duration>";
            final String MUSTER_DURATION_END = "</duration>";
            final String MUSTER_DESCRIPTION = "<teaserText>";
            final String MUSTER_DESCRIPTION_END = "</teaserText>";
            final String MUSTER_URL_MP4 = "<progressiveDownloadUrl>";
            //<progressiveDownloadUrl>http://x4100mp4dynonlc22033.f.o.l.lb.core-cdn.net/22033mdr/ondemand/4100mp4dynonl/FCMS-ad2e1bc5-d967-4791-b7ce-e5630252531a-c7cca1d51b4b.mp4</progressiveDownloadUrl>
            //<broadcastStartDate>23.08.2012 22:05</broadcastStartDate>
            int pos = 0;
            int pos1;
            int pos2;
            String titel, datum, zeit, width, urlMp4, urlMp4_klein, urlHD, urlSendung;
            long duration;
            String description;
            try {
                seite4 = getUrl.getUri_Utf(SENDERNAME, xmlSite, seite4, "Thema: " + thema);
                if ((pos = seite4.indexOf(MUSTER_START)) == -1) {
                    MSLog.fehlerMeldung(903656532, xmlSite);
                    return;
                }
                while ((pos = seite4.indexOf(MUSTER_TITEL, pos)) != -1) {
                    pos += MUSTER_TITEL.length();
                    if (seite4.indexOf(MUSTER_ENDE, pos) == -1) {
                        MSLog.fehlerMeldung(804142536, xmlSite);
                        continue;
                    }
                    titel = "";
                    datum = "";
                    zeit = "";
                    duration = 0;

                    if ((pos1 = seite4.indexOf(MUSTER_DURATION, pos)) != -1) {
                        pos1 += MUSTER_DURATION.length();
                        if ((pos2 = seite4.indexOf(MUSTER_DURATION_END, pos1)) != -1) {
                            try {
                                String d = seite4.substring(pos1, pos2);
                                if (!d.equals("")) {
                                    String[] parts = d.split(":");
                                    duration = 0;
                                    long power = 1;
                                    for (int i = parts.length - 1; i >= 0; i--) {
                                        duration += Long.parseLong(parts[i]) * power;
                                        power *= 60;
                                    }
                                }
                            } catch (Exception ex) {
                                MSLog.fehlerMeldung(313698749, ex, xmlSite);
                            }
                        }
                    }

                    description = seite4.extract(MUSTER_DESCRIPTION, MUSTER_DESCRIPTION_END, pos);
                    String subtitle = seite4.extract("<videoSubtitleUrl>", "<", pos); //<videoSubtitleUrl>http://www.mdr.de/mediathek/fernsehen/a-z/video209948-videoSubtitle_zc-ea9f5e14_zs-dea15b49.xml</videoSubtitleUrl>
                    urlSendung = seite4.extract("<htmlUrl>", "<", pos);
                    if (urlSendung.isEmpty()) {
                        urlSendung = filmSite;
                    }

                    pos1 = pos;
                    if ((pos2 = seite4.indexOf("<", pos)) != -1) {
                        titel = seite4.substring(pos1, pos2);
                    }
                    if ((pos1 = seite4.indexOf(MUSTER_DATUM, pos)) != -1) {
                        pos1 += MUSTER_DATUM.length();
                        if ((pos2 = seite4.indexOf("<", pos1)) != -1) {
                            datum = seite4.substring(pos1, pos2);
                            zeit = convertZeitXml(datum);
                            datum = convertDatumXml(datum);
                        }
                    }

                    // Film-URLs suchen
                    urlHD = seite4.extract("| MP4 Web XL |", MUSTER_URL_MP4, "<", pos);
                    urlMp4 = seite4.extract("| MP4 Web L |", MUSTER_URL_MP4, "<", pos);
                    if (urlMp4.isEmpty()) {
                        urlMp4 = seite4.extract("| MP4 Web L+ |", MUSTER_URL_MP4, "<", pos);
                    }
                    urlMp4_klein = seite4.extract("| MP4 Web M |", MUSTER_URL_MP4, "<", pos);

                    if (urlMp4.isEmpty()) {
                        urlMp4 = urlMp4_klein;
                        urlMp4_klein = "";
                    }
                    if (urlMp4.equals("")) {
                        MSLog.fehlerMeldung(326541230, new String[]{"keine URL: " + xmlSite, "Thema: " + thema, " UrlFeed: " + strUrlFeed});
                    } else {
                        if (!existiertSchon(thema, titel, datum, zeit)) {
                            meldung(urlMp4);
                            DatenFilm film = new DatenFilm(SENDERNAME, thema, urlSendung, titel, urlMp4, ""/*rtmpUrl*/, datum, zeit, duration, description);
                            film.addUrlKlein(urlMp4_klein, "");
                            film.addUrlHd(urlHD, "");
                            if (!subtitle.isEmpty()) {
                                film.addUrlSubtitle(subtitle);
                            }
                            addFilm(film);
                        }
                    }
                }
            } catch (Exception ex) {
                MSLog.fehlerMeldung(446286970, ex);
            }
        }
    }

    private String convertDatumXml(String datum) {
        //<broadcastStartDate>23.08.2012 22:05</broadcastStartDate>
        try {
            SimpleDateFormat sdfIn = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            Date filmDate = sdfIn.parse(datum);
            SimpleDateFormat sdfOut;
            sdfOut = new SimpleDateFormat("dd.MM.yyyy");
            datum = sdfOut.format(filmDate);
        } catch (Exception ex) {
            MSLog.fehlerMeldung(435209987, ex);
        }
        return datum;
    }

    private String convertZeitXml(String datum) {
        //<broadcastStartDate>23.08.2012 22:05</broadcastStartDate>
        try {
            SimpleDateFormat sdfIn = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            Date filmDate = sdfIn.parse(datum);
            SimpleDateFormat sdfOut;
            sdfOut = new SimpleDateFormat("HH:mm:ss");
            datum = sdfOut.format(filmDate);
        } catch (Exception ex) {
            MSLog.fehlerMeldung(102658736, ex);
        }
        return datum;
    }

    private synchronized String getListeTage() {
        return listeTage.pollFirst();
    }

    private synchronized boolean existiertSchon(String thema, String titel, String datum, String zeit) {
        // liefert true wenn schon in der Liste, ansonsten fügt es ein
        boolean gefunden = false;
        Iterator<String[]> it = listeGesucht.iterator();
        while (it.hasNext()) {
            String[] k = it.next();
            if (k[0].equalsIgnoreCase(thema) && k[1].equalsIgnoreCase(titel) && k[2].equalsIgnoreCase(datum) && k[3].equalsIgnoreCase(zeit)) {
                gefunden = true;
            }
        }
        if (!gefunden) {
            listeGesucht.add(new String[]{thema, titel, datum, zeit});
        }
        return gefunden;
    }
}
