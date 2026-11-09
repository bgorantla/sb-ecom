import { Alert, Skeleton } from '@mui/material'
import { Elements } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import PaymentForm from './PaymentForm';
import { createStripePaymentSecret } from '../../store/actions';

const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY);

const StripePayment = () => {
  const dispatch = useDispatch();
  const { clientSecret, selectedUserCheckoutAddress } = useSelector((state) => state.auth);
  const { totalPrice } = useSelector((state) => state.carts);
  const { isLoading, errorMessage } = useSelector((state) => state.errors);
  const addressId = selectedUserCheckoutAddress?.addressId;

  // Start a new payment each time this step opens. Only the address is sent:
  // the server decides how much to charge from the cart it has stored.
  useEffect(() => {
    if (addressId) {
      dispatch(createStripePaymentSecret({ addressId }));
    }
  }, [dispatch, addressId]);

  if (isLoading) {
    return (
      <div className='max-w-lg mx-auto'>
        <Skeleton />
      </div>
    )
  }

  if (errorMessage) {
    return (
      <div className='max-w-lg mx-auto p-4'>
        <Alert severity="error">{errorMessage}</Alert>
      </div>
    )
  }

  return (
    <>
      {clientSecret && (
        // key: a new payment gets a fresh Stripe form
        <Elements key={clientSecret} stripe={stripePromise} options={{ clientSecret }}>
          <PaymentForm clientSecret={clientSecret} totalPrice={totalPrice} />
        </Elements>
      )}
    </>
  )
}

export default StripePayment
