import React, { useState } from 'react';
import ReactDOM from 'react-dom';
import { X, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { LAYER } from '@/styles/layers';

const LABEL_CLASS = 'block text-sm font-medium text-tx-1 mb-1';
const INPUT_CLASS = 'w-full px-3 py-2 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent';

interface AuthModalFrameProps {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}

// 로그인 · 가입 창의 틀(화면을 덮는 바탕 · 제목 · 닫기 버튼)
export const AuthModalFrame: React.FC<AuthModalFrameProps> = ({ title, onClose, children }) =>
  ReactDOM.createPortal(
    <div style={{ position: 'fixed', inset: 0, zIndex: LAYER.modal, backgroundColor: 'rgba(0,0,0,0.5)', overflowY: 'auto' }}>
      <div className="flex min-h-full items-center justify-center p-4">
        <div className="bg-surface rounded-lg shadow-xl w-full max-w-md">
          <div className="flex items-center justify-between p-6 border-b">
            <h2 className="text-xl font-semibold text-tx-1">{title}</h2>
            <button
              onClick={onClose}
              aria-label="닫기"
              className="text-tx-3 hover:text-tx-2 transition-colors"
            >
              <X className="w-6 h-6" />
            </button>
          </div>
          <div className="p-6">{children}</div>
        </div>
      </div>
    </div>,
    document.body
  );

interface AuthTextFieldProps {
  id: string;
  label: string;
  type?: 'text' | 'email';
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}

// 이름표와 입력 칸
export const AuthTextField: React.FC<AuthTextFieldProps> = ({ id, label, type = 'text', value, onChange, placeholder }) => (
  <div>
    <label htmlFor={id} className={LABEL_CLASS}>
      {label}
    </label>
    <input
      type={type}
      id={id}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={INPUT_CLASS}
      placeholder={placeholder}
      required
    />
  </div>
);

interface PasswordFieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}

// 글자 보기 버튼이 있는 비밀번호 칸
export const PasswordField: React.FC<PasswordFieldProps> = ({ id, label, value, onChange, placeholder }) => {
  const [visible, setVisible] = useState(false);

  return (
    <div>
      <label htmlFor={id} className={LABEL_CLASS}>
        {label}
      </label>
      <div className="relative">
        <input
          type={visible ? 'text' : 'password'}
          id={id}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={`${INPUT_CLASS} pr-10`}
          placeholder={placeholder}
          required
        />
        <button
          type="button"
          onClick={() => setVisible(!visible)}
          aria-label={`${label} ${visible ? '숨기기' : '보기'}`}
          className="absolute inset-y-0 right-0 pr-3 flex items-center text-tx-3 hover:text-tx-2"
        >
          {visible ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
        </button>
      </div>
    </div>
  );
};

// 폼 오류 문구(없으면 안 그림)
export const FormError: React.FC<{ message: string }> = ({ message }) =>
  message ? <div className="text-danger text-sm bg-danger/10 p-3 rounded-md">{message}</div> : null;

interface SubmitButtonProps {
  loading: boolean;
  loadingText: string;
  children: React.ReactNode;
}

// 보내는 동안 잠기는 제출 버튼
export const SubmitButton: React.FC<SubmitButtonProps> = ({ loading, loadingText, children }) => (
  <button
    type="submit"
    disabled={loading}
    className="w-full bg-brand text-brand-ink py-2 px-4 rounded-md hover:bg-brand-dark focus:outline-none focus:ring-2 focus:ring-brand focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
  >
    {loading ? loadingText : children}
  </button>
);

// '또는' 구분선
export const OrDivider: React.FC = () => (
  <div className="my-6">
    <div className="relative">
      <div className="absolute inset-0 flex items-center">
        <div className="w-full border-t border-line-strong"></div>
      </div>
      <div className="relative flex justify-center text-sm">
        <span className="px-2 bg-surface text-tx-2">또는</span>
      </div>
    </div>
  </div>
);

interface GoogleButtonProps {
  label: string;
  loadingLabel: string;
  failMessage: string;
  onError: (message: string) => void;
}

// 구글 로그인 버튼(가입도 같은 흐름)
export const GoogleButton: React.FC<GoogleButtonProps> = ({ label, loadingLabel, failMessage, onError }) => {
  const { loginWithGoogle } = useAuth();
  const [loading, setLoading] = useState(false);

  const handleClick = async () => {
    setLoading(true);
    onError('');

    try {
      await loginWithGoogle();
    } catch (error) {
      console.error(failMessage, error);
      onError(failMessage);
      setLoading(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={loading}
      className="w-full flex items-center justify-center px-4 py-2 border border-line-strong rounded-md shadow-sm bg-surface text-sm font-medium text-tx-1 hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
    >
      <svg className="w-5 h-5 mr-2" viewBox="0 0 24 24">
        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
      </svg>
      {loading ? loadingLabel : label}
    </button>
  );
};

interface SwitchPromptProps {
  question: string;
  action: string;
  onClick: () => void;
}

// 로그인 · 가입 창을 바꾸는 안내 줄
export const SwitchPrompt: React.FC<SwitchPromptProps> = ({ question, action, onClick }) => (
  <div className="mt-6 text-center">
    <p className="text-sm text-tx-2">
      {question}{' '}
      <button
        onClick={onClick}
        className="text-brand hover:text-brand font-medium"
      >
        {action}
      </button>
    </p>
  </div>
);
