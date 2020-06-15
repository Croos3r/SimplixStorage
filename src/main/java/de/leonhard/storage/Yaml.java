package de.leonhard.storage;

import de.leonhard.storage.internal.FileData;
import de.leonhard.storage.internal.FileType;
import de.leonhard.storage.internal.FlatFile;
import de.leonhard.storage.internal.editor.yaml.SimpleYamlReader;
import de.leonhard.storage.internal.editor.yaml.SimpleYamlWriter;
import de.leonhard.storage.internal.editor.yaml.YamlEditor;
import de.leonhard.storage.internal.editor.yaml.YamlParser;
import de.leonhard.storage.internal.settings.ConfigSettings;
import de.leonhard.storage.internal.settings.DataType;
import de.leonhard.storage.internal.settings.ReloadSettings;
import de.leonhard.storage.util.FileUtils;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Cleanup;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Getter
public class Yaml extends FlatFile {

  protected final InputStream inputStream;
  protected final YamlEditor yamlEditor;
  protected final YamlParser parser;
  @Setter
  private ConfigSettings configSettings = ConfigSettings.SKIP_COMMENTS;

  public Yaml(final Yaml yaml) {
    super(yaml.getFile());
    fileData = yaml.getFileData();
    yamlEditor = yaml.getYamlEditor();
    parser = yaml.getParser();
    configSettings = yaml.getConfigSettings();
    inputStream = yaml.getInputStream().orElse(null);
  }

  public Yaml(final String name, @Nullable final String path) {
    this(name, path, null, null, null, null);
  }

  public Yaml(
      final String name,
      @Nullable final String path,
      @Nullable final InputStream inputStream) {
    this(name, path, inputStream, null, null, null);
  }

  public Yaml(
      final String name,
      @Nullable final String path,
      @Nullable final InputStream inputStream,
      @Nullable final ReloadSettings reloadSettings,
      @Nullable final ConfigSettings configSettings,
      @Nullable final DataType dataType) {
    super(name, path, FileType.YAML);
    this.inputStream = inputStream;

    if (create() && inputStream != null) {
      FileUtils.writeToFile(file, inputStream);
    }

    yamlEditor = new YamlEditor(file);
    parser = new YamlParser(yamlEditor);

    if (reloadSettings != null) {
      this.reloadSettings = reloadSettings;
    }

    if (configSettings != null) {
      this.configSettings = configSettings;
    }

    if (dataType != null) {
      this.dataType = dataType;
    } else {
      this.dataType = DataType.fromConfigSettings(configSettings);
    }

    forceReload();
  }

  public Yaml(final File file) {
    this(file.getName(), FileUtils.getParentDirPath(file));
  }

  // ----------------------------------------------------------------------------------------------------
  // Methods to override (Points where YAML is unspecific for typical FlatFiles)
  // ----------------------------------------------------------------------------------------------------

  public Yaml addDefaultsFromInputStream() {
    return addDefaultsFromInputStream(getInputStream().orElse(null));
  }

  public Yaml addDefaultsFromInputStream(@Nullable final InputStream inputStream) {
    reloadIfNeeded();
    // Creating & setting defaults
    if (inputStream == null) {
      return this;
    }

    try {
      final Map<String, Object> data = new SimpleYamlReader(
          new InputStreamReader(inputStream)).readToMap();

      final FileData newData = new FileData(data, DataType.UNSORTED);

      for (final String key : newData.keySet()) {
        if (!fileData.containsKey(key)) {
          fileData.insert(key, newData.get(key));
        }
      }

      write();
    } catch (final Exception ex) {
      ex.printStackTrace();
    }

    return this;
  }

  // ----------------------------------------------------------------------------------------------------
  // Abstract methods to implement
  // ----------------------------------------------------------------------------------------------------

  @Override
  protected Map<String, Object> readToMap() throws IOException {
    @Cleanup final SimpleYamlReader reader = new SimpleYamlReader(
        new FileReader(getFile()));
    return reader.readToMap();
  }

  @Override
  protected void write(final FileData data) throws IOException {
    // If Comments shouldn't be preserved
    if (!ConfigSettings.PRESERVE_COMMENTS.equals(configSettings)) {
      write0(fileData);
      return;
    }

    final List<String> unEdited = yamlEditor.read();
    write0(fileData);
    yamlEditor.write(parser.parseLines(unEdited, yamlEditor.readKeys()));
  }

  // Writing without comments
  private void write0(final FileData fileData) throws IOException {
    @Cleanup final SimpleYamlWriter writer = new SimpleYamlWriter(file);
    writer.write(fileData.toMap());
  }

  // ----------------------------------------------------------------------------------------------------
  // Specific utility methods for YAML
  // ----------------------------------------------------------------------------------------------------

  public final List<String> getHeader() {
    return yamlEditor.readHeader();
  }

  public final void setHeader(final List<String> header) {
    yamlEditor.setHeader(header);
  }

  public final void setHeader(final String... header) {
    setHeader(Arrays.asList(header));
  }

  public final void addHeader(final List<String> toAdd) {
    yamlEditor.addHeader(toAdd);
  }

  public final void addHeader(final String... header) {
    addHeader(Arrays.asList(header));
  }

	public final void framedHeader (final String... header) {
		List <String> stringList = new ArrayList <>();
		String border = "# +----------------------------------------------------+ #";
		stringList.add(border);

		for (String line : header) {
			StringBuilder builder = new StringBuilder();
			if (line.length() > 50) {
				continue;
			}

			int length = (50 - line.length()) / 2;
			StringBuilder finalLine = new StringBuilder(line);

			for (int i = 0; i < length; i++) {
				finalLine.append(" ");
				finalLine.reverse();
				finalLine.append(" ");
				finalLine.reverse();
			}

			if (line.length() % 2 != 0) {
				finalLine.append(" ");
			}

			builder.append("# < ").append(finalLine.toString()).append(" > #");
			stringList.add(builder.toString());
		}
		stringList.add(border);
		setHeader(stringList);
	}

  public final Optional<InputStream> getInputStream() {
    return Optional.ofNullable(inputStream);
  }
}
