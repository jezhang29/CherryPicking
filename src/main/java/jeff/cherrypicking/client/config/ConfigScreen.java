package jeff.cherrypicking.client.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the config screen out of {@link Settings}.
 *
 * <p>There is no per-setting code here and there must not be. The screen is a
 * projection of the registry: tabs come from {@link Section#tab()}, groups from
 * {@link Section#group()}, widgets from each setting's {@link Control}. Adding a
 * setting to the registry is the whole job of putting it on the screen.
 *
 * <p>YACL is a hard {@code depends}, so this never fails to open and
 * {@link HubEntry} never has to report that it could not.
 */
public final class ConfigScreen {
	private ConfigScreen() {
	}

	/** @param parent the screen to return to when this one closes; may be null */
	public static Screen build(Screen parent) {
		YetAnotherConfigLib.Builder screen = YetAnotherConfigLib.createBuilder()
				.title(Component.literal("CherryPicking"))
				.save(ConfigFile::save);

		for (Map.Entry<String, List<Section>> tab : tabs().entrySet()) {
			ConfigCategory.Builder category = ConfigCategory.createBuilder()
					.name(Component.literal(tab.getKey()));

			for (Section section : tab.getValue()) {
				OptionGroup.Builder group = OptionGroup.createBuilder()
						.name(Component.literal(section.group()));
				boolean any = false;
				for (Setting<?> setting : Settings.all()) {
					if (setting.section() == section) {
						group.option(option(setting));
						any = true;
					}
				}
				// A section nothing has been filed under yet would otherwise draw
				// as a heading with nothing beneath it.
				if (any) {
					category.group(group.build());
				}
			}

			screen.category(category.build());
		}

		return screen.build().generateScreen(parent);
	}

	/** The sections of each tab, both in declaration order. */
	private static Map<String, List<Section>> tabs() {
		Map<String, List<Section>> byTab = new LinkedHashMap<>();
		for (Section section : Section.values()) {
			byTab.computeIfAbsent(section.tab(), tab -> new ArrayList<>()).add(section);
		}
		return byTab;
	}

	/**
	 * The unchecked casts are what the switch has just proved. {@link Control} is
	 * sealed and {@code Control<T>} is the same {@code T} as the setting's, so a
	 * {@code Control.Whole} can only have come from a {@code Setting<Integer>}.
	 */
	@SuppressWarnings("unchecked")
	private static Option<?> option(Setting<?> setting) {
		return switch (setting.control()) {
			case Control.Flag ignored -> flag((Setting<Boolean>) setting);
			case Control.Whole control -> whole((Setting<Integer>) setting, control);
			case Control.Real control -> real((Setting<Double>) setting, control);
			case Control.Choice<?> control -> choice(setting, control);
		};
	}

	private static Option<Boolean> flag(Setting<Boolean> setting) {
		return base(setting)
				.controller(option -> BooleanControllerBuilder.create(option)
						.onOffFormatter()
						.coloured(true))
				.build();
	}

	private static Option<Integer> whole(Setting<Integer> setting, Control.Whole control) {
		return base(setting)
				.controller(option -> IntegerSliderControllerBuilder.create(option)
						.range(control.min(), control.max())
						.step(control.step())
						.formatValue(value -> Component.literal(
								value == 0 && !control.zeroLabel().isEmpty()
										? control.zeroLabel()
										: value + " " + control.unit())))
				.build();
	}

	private static Option<Double> real(Setting<Double> setting, Control.Real control) {
		return base(setting)
				.controller(option -> DoubleSliderControllerBuilder.create(option)
						.range(control.min(), control.max())
						.step(control.step())
						.formatValue(value -> Component.literal(written(value, control.format()))))
				.build();
	}

	private static <E extends Enum<E>> Option<E> choice(Setting<?> setting, Control.Choice<E> control) {
		@SuppressWarnings("unchecked")
		Setting<E> typed = (Setting<E>) setting;
		return base(typed)
				.controller(option -> EnumControllerBuilder.create(option)
						.enumClass(control.type())
						.formatValue(value -> Component.literal(control.label().apply(value))))
				.build();
	}

	/**
	 * The half of every option that does not depend on its type: the name, the
	 * one-sentence description, and the binding back to the field that owns the
	 * value.
	 */
	private static <T> Option.Builder<T> base(Setting<T> setting) {
		return Option.<T>createBuilder()
				.name(Component.literal(setting.label()))
				.description(OptionDescription.of(Component.literal(setting.blurb())))
				.binding(setting.fallback(), setting::value, setting::value);
	}

	private static String written(double value, Control.Format format) {
		return switch (format) {
			case PLAIN -> String.format(Locale.ROOT, "%.2f", value);
			case BLOCKS -> String.format(Locale.ROOT, "%.2f blocks", value);
			case SECONDS -> String.format(Locale.ROOT, "%.1f s", value);
			case PERCENT -> Math.round(value * 100.0) + "%";
		};
	}
}
