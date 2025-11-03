import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useResponsive } from "@/hooks/useResponsive";

describe("Responsive Behavior", () => {
  let originalInnerWidth: number;

  beforeEach(() => {
    originalInnerWidth = window.innerWidth;
  });

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    });
    window.dispatchEvent(new Event("resize"));
  });

  const testBreakpoint = (width: number, expected: string) => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: width,
    });
    window.dispatchEvent(new Event("resize"));

    const { result } = renderHook(() => useResponsive());
    expect(result.current.breakpoint).toBe(expected);
  };

  it("returns xs breakpoint for 1280px", () => {
    testBreakpoint(1280, "xs");
  });

  it("returns sm breakpoint for 1366px", () => {
    testBreakpoint(1366, "sm");
  });

  it("returns md breakpoint for 1600px", () => {
    testBreakpoint(1600, "md");
  });

  it("returns lg breakpoint for 1920px", () => {
    testBreakpoint(1920, "lg");
  });

  it("returns xl breakpoint for 2560px", () => {
    testBreakpoint(2560, "xl");
  });

  it("calculates correct screen dimensions", () => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: 1920,
    });
    Object.defineProperty(window, "innerHeight", {
      writable: true,
      configurable: true,
      value: 1080,
    });
    window.dispatchEvent(new Event("resize"));

    const { result } = renderHook(() => useResponsive());
    expect(result.current.screenWidth).toBe(1920);
    expect(result.current.screenHeight).toBe(1080);
  });
});
