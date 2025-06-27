package Base;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ChromeDriverSetup {

	private static String getChromeVersion() throws IOException {
		String[] command = { "cmd", "/c",
				"wmic datafile where name=\"C:\\\\Program Files\\\\Google\\\\Chrome\\\\Application\\\\chrome.exe\" get Version /value" };
		Process process = Runtime.getRuntime().exec(command);
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("Version=")) {
				return line.split("=")[1].trim();
			}
		}
		return null;
	}

	private static String getNearestChromeDriverVersion(String chromeVersion) throws IOException {
		String urlString = "https://googlechromelabs.github.io/chrome-for-testing/latest-patch-versions-per-build-with-downloads.json";
		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.connect();

		if (conn.getResponseCode() != 200) {
			throw new RuntimeException("Failed to connect: HTTP error code : " + conn.getResponseCode());
		}

		JSONTokener tokener = new JSONTokener(conn.getInputStream());
		JSONObject jsonObject = new JSONObject(tokener);

		String[] chromeVersionParts = chromeVersion.split("\\.");
		String majorMinorPatch = chromeVersionParts[0] + "." + chromeVersionParts[1] + "." + chromeVersionParts[2];
		String majorMinor = chromeVersionParts[0] + "." + chromeVersionParts[1];

		TreeMap<String, String> availableVersions = new TreeMap<>((v1, v2) -> {
			String[] v1Parts = v1.split("\\.");
			String[] v2Parts = v2.split("\\.");
			for (int i = 0; i < v1Parts.length; i++) {
				int cmp = Integer.compare(Integer.parseInt(v1Parts[i]), Integer.parseInt(v2Parts[i]));
				if (cmp != 0)
					return cmp;
			}
			return 0;
		});

		JSONObject builds = jsonObject.getJSONObject("builds");
		
		for (String key : builds.keySet()) {
			JSONObject versionData = builds.getJSONObject(key);
			if (!versionData.has("downloads")) {
				continue;
			}
			JSONObject downloads = versionData.getJSONObject("downloads");
			if (!downloads.has("chromedriver")) {
				continue;
			}
			JSONArray chromedrivers = downloads.getJSONArray("chromedriver");
			for (int i = 0; i < chromedrivers.length(); i++) {
				JSONObject driver = chromedrivers.getJSONObject(i);
				if (driver.getString("platform").equals("win64")) {
					availableVersions.put(key, driver.getString("url"));
					break;
				}
			}
		}

		// Try to find the nearest version based on major.minor.patch
		String nearestVersion = availableVersions.floorKey(majorMinorPatch);
		if (nearestVersion != null) {
			return availableVersions.get(nearestVersion);
		} else {
			// If not found, try to find the nearest version based on major.minor
			nearestVersion = availableVersions.floorKey(majorMinor);
			if (nearestVersion != null) {
				return availableVersions.get(nearestVersion);
			} else {
				throw new RuntimeException("No suitable ChromeDriver version found.");
			}
		}
	}

	private static void downloadFile(String urlString, String destination) throws IOException {
		URL url = new URL(urlString);
		try (InputStream in = url.openStream()) {
			Files.copy(in, Paths.get(destination), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void unzip(String zipFilePath, String destDir) throws IOException {
		try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(Paths.get(zipFilePath)))) {
			java.util.zip.ZipEntry entry = zipIn.getNextEntry();
			while (entry != null) {
				String filePath = destDir + java.io.File.separator + entry.getName();
				if (!entry.isDirectory()) {
					File outFile = new File(filePath);
					outFile.getParentFile().mkdirs();
					try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
						byte[] bytesIn = new byte[4096];
						int read;
						while ((read = zipIn.read(bytesIn)) != -1) {
							bos.write(bytesIn, 0, read);
						}
					}
				} else {
					Files.createDirectories(Paths.get(filePath));
				}
				zipIn.closeEntry();
				entry = zipIn.getNextEntry();
			}
		}
	}

	public static void main(String[] args) {
		try {
			String chromeVersion = getChromeVersion();
			if (chromeVersion == null) {
				System.out.println("Unable to find Chrome version.");
				return;
			}
			System.out.println("Chrome Version: " + chromeVersion);

			String chromeDriverUrl = getNearestChromeDriverVersion(chromeVersion);
			if (chromeDriverUrl == null) {
				System.out.println("Unable to find ChromeDriver version.");
				return;
			}
			System.out.println("ChromeDriver URL: " + chromeDriverUrl);

			String zipPath = "chromedriver.zip";
			downloadFile(chromeDriverUrl, zipPath);
			unzip(zipPath, "chromedriver");

			// Clean up
			Files.delete(Paths.get(zipPath));

			System.out.println("ChromeDriver downloaded and unzipped successfully.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}