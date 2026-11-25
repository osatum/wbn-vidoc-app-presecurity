export function hasQuestionnaireOption(options: unknown, option: string): boolean {
  if (options instanceof Set) {
    return options.has(option);
  }

  return Array.isArray(options) && options.includes(option);
}

export function questionnaire2025Link(
  year: number | undefined,
  selected: boolean | null | undefined,
  url: string | null | undefined,
): string | undefined {
  return year === 2025 && selected === true && url?.trim() ? url : undefined;
}
