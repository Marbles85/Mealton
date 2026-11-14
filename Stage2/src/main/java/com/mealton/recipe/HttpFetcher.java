package com.mealton.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * The real internet access used by {@link RecipeImporter}. Only http(s) links
 * to public addresses are opened, pages are capped at 3 MB and pictures at 10
 * MB.
 */
public class HttpFetcher implements RecipeImporter.Fetcher {
	private static final int MAX_PAGE = 3 * 1024 * 1024;
	private static final int MAX_IMAGE = 10 * 1024 * 1024;
	private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10)).build();

	@Override
	public String page(String url) throws IOException {
		HttpResponse<InputStream> response = send(url, "text/html,application/xhtml+xml");
		try (InputStream in = response.body()) {
			return new String(in.readNBytes(MAX_PAGE), StandardCharsets.UTF_8);
		}
	}

	@Override
	public Path image(String url) throws IOException {
		HttpResponse<InputStream> response = send(url, "image/*");
		String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
		String extension;
		if (type.contains("png")) {
			extension = "png";
		} else if (type.contains("jpeg") || type.contains("jpg")) {
			extension = "jpg";
		} else if (type.contains("gif")) {
			extension = "gif";
		} else {
			response.body().close();
			return null; // for example webp: the screen cannot show it
		}
		Path file = Files.createTempFile("mealton-import-", "." + extension);
		file.toFile().deleteOnExit();
		try (InputStream in = response.body()) {
			byte[] bytes = in.readNBytes(MAX_IMAGE + 1);
			if (bytes.length > MAX_IMAGE) {
				Files.deleteIfExists(file);
				return null;
			}
			Files.write(file, bytes);
		}
		return file;
	}

	private HttpResponse<InputStream> send(String url, String accept) throws IOException {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new IOException("that is not a valid link");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
			throw new IOException("only web links are allowed");
		}
		for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
			if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
					|| address.isAnyLocalAddress()) {
				throw new IOException("that address is not a public website");
			}
		}
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
				.header("User-Agent", "Mealton/1.0 (student project recipe import)").header("Accept", accept).GET()
				.build();
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() >= 400) {
				response.body().close();
				throw new IOException("the site answered with error " + response.statusCode());
			}
			return response;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("the download was interrupted");
		}
	}
}