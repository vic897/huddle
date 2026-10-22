import React from 'react';

type Variant = 'primary' | 'secondary';
export type Size = 'small' | 'default' | 'large';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  className?: string;
  asChild?: boolean;
}

export const Button: React.FC<ButtonProps> = ({ variant = 'primary', size = 'default', className, children, asChild = false, ...props }) => {
  const base = 'font-semibold transition-colors shadow-elev-3 rounded-md disabled:opacity-50';
  const variants: Record<Variant, string> = {
    primary: 'btn-primary',
    secondary: 'btn-secondary',
  };
  const sizes: Record<Size, string> = {
    small: 'px-3 py-1.5 text-sm',
    default: 'px-4 py-2',
    large: 'px-5 py-2.5 text-sm',
  };
  const content = (
    <button className={`${base} ${variants[variant]} ${sizes[size]} ${className ?? ''}`} {...props}>
      {children}
    </button>
  );
  if (asChild && React.isValidElement(children)) {
    const child = children as React.ReactElement<any>;
    const mergedClass = `${base} ${variants[variant]} ${sizes[size]} ${className ?? ''} ${(child.props as any).className || ''}`.trim();
    return React.cloneElement(child, { className: mergedClass, ...props } as any);
  }
  return content;
};

export default Button;
