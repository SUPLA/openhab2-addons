/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.voice.pollytts.internal.cloudapi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a cache for the retrieved audio data. It will preserve them in the file system,
 * as audio files with an additional .txt file to indicate what content is in the audio file.
 *
 * @author Robert Hillman - Initial contribution
 */
public class CachedPollyTTSCloudImpl extends PollyTTSCloudImpl {

    private static final int READ_BUFFER_SIZE = 4096;

    private final Logger logger = LoggerFactory.getLogger(CachedPollyTTSCloudImpl.class);

    private final File cacheFolder;

    /**
     * Create the file folder to hold the the cached speech files.
     * check to make sure the directory exist and
     * create it if necessary
     */
    public CachedPollyTTSCloudImpl(PollyTTSConfig config, File cacheFolder) throws IOException {
        super(config);
        this.cacheFolder = cacheFolder;
    }

    /**
     * Fetch the specified text as an audio file.
     * The audio file will be obtained from the cached folder if it
     * exist or generated by use to the external voice service.
     * The cached file txt description time stamp will be updated
     * to identify last use.
     */
    public File getTextToSpeechAsFile(String text, String label, String audioFormat) throws IOException {
        String fileNameInCache = getUniqueFilenameForText(text, label);
        // check if in cache
        File audioFileInCache = new File(cacheFolder, fileNameInCache + "." + audioFormat.toLowerCase());
        if (audioFileInCache.exists()) {
            // update use date
            updateTimeStamp(audioFileInCache);
            updateTimeStamp(new File(cacheFolder, fileNameInCache + ".txt"));
            purgeAgedFiles();
            return audioFileInCache;
        }

        // if not in cache, get audio data and put to cache
        try (InputStream is = getTextToSpeech(text, label, audioFormat);
                FileOutputStream fos = new FileOutputStream(audioFileInCache);) {
            copyStream(is, fos);
            // write text to file for transparency too
            // this allows to know which contents is in which audio file
            File txtFileInCache = new File(cacheFolder, fileNameInCache + ".txt");
            writeText(txtFileInCache, text);
            // return from cache
            return audioFileInCache;
        } catch (IOException ex) {
            logger.warn("Could not write {} to cache, return null", audioFileInCache, ex);
            return null;
        }
    }

    /**
     * Gets a unique filename for a give text, by creating a MD5 hash of it. It
     * will be preceded by the voice label.
     *
     * Sample: "Robert_00a2653ac5f77063bc4ea2fee87318d3"
     */
    private String getUniqueFilenameForText(String text, String label) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            logger.error("Could not create MD5 hash for '{}'", text, ex);
            return null;
        }
        byte[] md5Hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
        BigInteger bigInt = new BigInteger(1, md5Hash);
        String hashtext = bigInt.toString(16);
        // Now we need to zero pad it if you actually want the full 32
        // chars.
        while (hashtext.length() < 32) {
            hashtext = "0" + hashtext;
        }
        String fileName = label + "_" + hashtext;
        return fileName;
    }

    // helper methods

    private void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bytes = new byte[READ_BUFFER_SIZE];
        int read = inputStream.read(bytes, 0, READ_BUFFER_SIZE);
        while (read > 0) {
            outputStream.write(bytes, 0, read);
            read = inputStream.read(bytes, 0, READ_BUFFER_SIZE);
        }
    }

    private void writeText(File file, String text) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void updateTimeStamp(File file) throws IOException {
        // update use date for cache management
        file.setLastModified(System.currentTimeMillis());
    }

    private void purgeAgedFiles() throws IOException {
        // just exit if expiration set to 0/disabled
        if (config.getExpireDate() == 0) {
            return;
        }
        long now = new Date().getTime();
        long diff = now - config.getLastDelete();
        // only execute ~ once every 2 days if cache called
        long oneDayMillis = TimeUnit.DAYS.toMillis(1);
        logger.debug("PollyTTS cache cleaner lastdelete {}", diff);
        if (diff > (2 * oneDayMillis)) {
            config.setLastDelete(now);
            long xDaysAgo = config.getExpireDate() * oneDayMillis;
            // Now search folders and delete old files
            int filesDeleted = 0;
            for (File file : cacheFolder.listFiles()) {
                diff = now - file.lastModified();
                if (diff > xDaysAgo) {
                    filesDeleted++;
                    file.delete();
                }
            }
            logger.debug("PollyTTS cache cleaner deleted '{}' aged files", filesDeleted);
        }
    }
}
