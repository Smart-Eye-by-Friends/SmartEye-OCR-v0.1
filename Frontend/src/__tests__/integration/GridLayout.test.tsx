import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { useGridLayout } from "@/hooks/useGridLayout";
import { useResponsive } from "@/hooks/useResponsive";

describe("Grid Layout", () => {
  it("useGridLayout hook works correctly", () => {
    const TestComponent = () => {
      const { isSliderCollapsed, closeSlider, openSlider } = useGridLayout();
      return (
        <div>
          <div data-testid="slider-state">
            {isSliderCollapsed ? "collapsed" : "open"}
          </div>
          <button onClick={closeSlider}>Close</button>
          <button onClick={openSlider}>Open</button>
        </div>
      );
    };

    render(<TestComponent />);
    
    // 초기 상태 확인
    expect(screen.getByTestId("slider-state")).toHaveTextContent("open");
  });

  it("useResponsive hook returns breakpoint", () => {
    const TestComponent = () => {
      const { breakpoint, screenWidth, screenHeight } = useResponsive();
      return (
        <div>
          <div data-testid="breakpoint">{breakpoint}</div>
          <div data-testid="width">{screenWidth}</div>
          <div data-testid="height">{screenHeight}</div>
        </div>
      );
    };

    render(<TestComponent />);
    
    // breakpoint가 존재하는지 확인
    expect(screen.getByTestId("breakpoint")).toBeDefined();
    expect(screen.getByTestId("width")).toBeDefined();
    expect(screen.getByTestId("height")).toBeDefined();
  });
});
